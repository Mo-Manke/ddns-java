package top.hanlin.publicipupload.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.dnspod.v20210323.DnspodClient;
import com.tencentcloudapi.dnspod.v20210323.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.hanlin.publicipupload.entity.DdnsTask;
import top.hanlin.publicipupload.util.IP_SERVICES;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * DDNS定时任务管理服务
 */
@Slf4j
@Service
public class DdnsTaskService {
    
    private static final String TASKS_FILE = "ddns_tasks.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // 任务列表
    private final Map<String, DdnsTask> tasks = new ConcurrentHashMap<>();
    // 定时任务调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    // 任务Future映射
    private final Map<String, ScheduledFuture<?>> taskFutures = new ConcurrentHashMap<>();
    // 操作日志队列（最多保留100条）
    private final List<Map<String, String>> operationLogs = Collections.synchronizedList(new LinkedList<>());
    private static final int MAX_LOGS = 100;
    
    @PostConstruct
    public void init() {
        loadTasks();
        // 启动所有已启用的任务
        tasks.values().stream()
            .filter(DdnsTask::isEnabled)
            .forEach(this::startTask);
        log.info("DDNS任务服务初始化完成，已加载 {} 个任务", tasks.size());
    }
    
    @PreDestroy
    public void destroy() {
        scheduler.shutdownNow();
        log.info("DDNS任务服务已关闭");
    }
    
    /**
     * 获取所有任务
     */
    public List<DdnsTask> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }
    
    /**
     * 获取指定账号的任务
     */
    public List<DdnsTask> getTasksByAccount(String secretId) {
        return tasks.values().stream()
            .filter(t -> t.getSecretId().equals(secretId))
            .toList();
    }
    
    /**
     * 添加任务
     */
    public DdnsTask addTask(DdnsTask task) {
        // 生成任务ID
        task.setId(UUID.randomUUID().toString().substring(0, 8));
        task.setFullDomain(buildFullDomain(task.getSubdomain(), task.getDomain()));
        task.setStatus("stopped");
        task.setEnabled(false);
        
        tasks.put(task.getId(), task);
        saveTasks();
        
        log.info("添加DDNS任务: {}", task.getFullDomain());
        return task;
    }
    
    /**
     * 更新任务配置
     */
    public DdnsTask updateTask(String taskId, int interval, String ipServiceUrl, String ipServiceName) {
        DdnsTask task = tasks.get(taskId);
        if (task == null) {
            return null;
        }
        
        boolean wasEnabled = task.isEnabled();
        
        // 如果正在运行，先停止
        if (wasEnabled) {
            stopTask(taskId);
        }
        
        task.setInterval(interval);
        task.setIpServiceUrl(ipServiceUrl);
        task.setIpServiceName(ipServiceName);
        
        // 如果之前是启用的，重新启动
        if (wasEnabled) {
            startTask(task);
        }
        
        saveTasks();
        log.info("更新DDNS任务配置: {} interval={}s service={}", task.getFullDomain(), interval, ipServiceName);
        return task;
    }
    
    /**
     * 启动任务
     */
    public boolean startTask(String taskId) {
        DdnsTask task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        return startTask(task);
    }
    
    private boolean startTask(DdnsTask task) {
        // 先停止已有的调度
        stopTask(task.getId());
        
        task.setEnabled(true);
        task.setStatus("running");
        
        // 立即执行一次
        try {
            executeTask(task);
        } catch (Exception e) {
            log.error("首次执行DDNS任务失败: {} - {}", task.getFullDomain(), e.getMessage());
        }
        
        // 设置定时任务（包装异常处理，防止任务因异常而停止）
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    log.debug("定时执行DDNS任务: {}", task.getFullDomain());
                    executeTask(task);
                } catch (Exception e) {
                    log.error("定时执行DDNS任务异常: {} - {}", task.getFullDomain(), e.getMessage(), e);
                    addOperationLog("error", "[DDNS] " + task.getFullDomain() + " 定时执行异常: " + e.getMessage());
                }
            },
            task.getInterval(),
            task.getInterval(),
            TimeUnit.SECONDS
        );
        
        taskFutures.put(task.getId(), future);
        saveTasks();
        
        log.info("启动DDNS任务: {} 间隔: {}秒", task.getFullDomain(), task.getInterval());
        addOperationLog("info", "[DDNS] 任务已启动: " + task.getFullDomain() + " 间隔: " + task.getInterval() + "秒");
        return true;
    }
    
    /**
     * 停止任务
     */
    public boolean stopTask(String taskId) {
        DdnsTask task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        
        ScheduledFuture<?> future = taskFutures.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
        
        task.setEnabled(false);
        task.setStatus("stopped");
        saveTasks();
        
        log.info("停止DDNS任务: {}", task.getFullDomain());
        return true;
    }
    
    /**
     * 删除任务（同时删除云端DNS记录）
     */
    public boolean deleteTask(String taskId) {
        stopTask(taskId);
        DdnsTask removed = tasks.remove(taskId);
        if (removed != null) {
            // 删除云端DNS记录
            try {
                deleteDnsRecord(removed);
                log.info("已删除云端DNS记录: {}", removed.getFullDomain());
            } catch (Exception e) {
                log.warn("删除云端DNS记录失败: {} - {}", removed.getFullDomain(), e.getMessage());
            }
            saveTasks();
            log.info("删除DDNS任务: {}", removed.getFullDomain());
            return true;
        }
        return false;
    }
    
    /**
     * 删除DNS记录（根据provider选择）
     */
    private void deleteDnsRecord(DdnsTask task) throws Exception {
        if ("腾讯云".equals(task.getProvider())) {
            deleteTencentDnsRecord(task);
        } else if ("阿里云".equals(task.getProvider())) {
            deleteAliyunDnsRecord(task);
        }
    }
    
    /**
     * 删除腾讯云DNS记录
     */
    private void deleteTencentDnsRecord(DdnsTask task) throws Exception {
        Credential cred = new Credential(task.getSecretId(), task.getSecretKey());
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("dnspod.tencentcloudapi.com");
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        DnspodClient client = new DnspodClient(cred, "", clientProfile);
        
        // 查找记录ID
        DescribeRecordListRequest listReq = new DescribeRecordListRequest();
        listReq.setDomain(task.getDomain());
        listReq.setSubdomain(task.getSubdomain());
        DescribeRecordListResponse listResp = client.DescribeRecordList(listReq);
        
        if (listResp.getRecordList() != null && listResp.getRecordList().length > 0) {
            for (RecordListItem record : listResp.getRecordList()) {
                if ("A".equals(record.getType())) {
                    DeleteRecordRequest deleteReq = new DeleteRecordRequest();
                    deleteReq.setDomain(task.getDomain());
                    deleteReq.setRecordId(record.getRecordId());
                    client.DeleteRecord(deleteReq);
                    log.info("删除DNS记录: {} (ID: {})", task.getFullDomain(), record.getRecordId());
                }
            }
        }
    }
    
    /**
     * 删除阿里云DNS记录
     */
    private void deleteAliyunDnsRecord(DdnsTask task) throws Exception {
        com.aliyun.alidns20150109.Client client = createAliyunClient(task.getSecretId(), task.getSecretKey());
        
        // 查找记录ID
        com.aliyun.alidns20150109.models.DescribeDomainRecordsRequest listReq = 
            new com.aliyun.alidns20150109.models.DescribeDomainRecordsRequest()
                .setDomainName(task.getDomain())
                .setRRKeyWord(task.getSubdomain())
                .setType("A");
        com.aliyun.alidns20150109.models.DescribeDomainRecordsResponse listResp = client.describeDomainRecords(listReq);
        
        if (listResp.getBody().getDomainRecords() != null && 
            listResp.getBody().getDomainRecords().getRecord() != null) {
            for (var record : listResp.getBody().getDomainRecords().getRecord()) {
                if (task.getSubdomain().equals(record.getRR()) && "A".equals(record.getType())) {
                    com.aliyun.alidns20150109.models.DeleteDomainRecordRequest deleteReq = 
                        new com.aliyun.alidns20150109.models.DeleteDomainRecordRequest()
                            .setRecordId(record.getRecordId());
                    client.deleteDomainRecord(deleteReq);
                    log.info("删除阿里云DNS记录: {} (ID: {})", task.getFullDomain(), record.getRecordId());
                }
            }
        }
    }
    
    /**
     * 手动执行一次任务
     */
    public Map<String, Object> executeTaskNow(String taskId) {
        DdnsTask task = tasks.get(taskId);
        if (task == null) {
            return Map.of("success", false, "message", "任务不存在");
        }
        return executeTask(task);
    }
    
    /**
     * 执行DDNS更新
     */
    private Map<String, Object> executeTask(DdnsTask task) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取当前IP（优先使用用户选择的服务，失败则自动切换备用服务）
            String currentIp = fetchIPWithFallback(task);
            if (currentIp == null || currentIp.isEmpty()) {
                throw new Exception("所有IP服务均不可用");
            }
            
            // 获取域名当前解析的IP
            String dnsIp = resolveDomainIp(task.getFullDomain());
            
            // 检查是否需要更新：本地IP变化 或 DNS解析IP与本地IP不一致
            boolean localIpChanged = !currentIp.equals(task.getLastIp());
            boolean dnsIpMismatch = dnsIp != null && !currentIp.equals(dnsIp);
            
            if (!localIpChanged && !dnsIpMismatch) {
                log.debug("IP未变化，跳过更新: {} -> {}", task.getFullDomain(), currentIp);
                addOperationLog("info", "[DDNS] " + task.getFullDomain() + " IP未变化: " + currentIp);
                result.put("success", true);
                result.put("message", "IP未变化");
                result.put("ip", currentIp);
                return result;
            }
            
            // 记录更新原因
            if (dnsIpMismatch && !localIpChanged) {
                log.info("DNS解析IP与本地IP不一致，需要更新: {} DNS={} 本地={}", task.getFullDomain(), dnsIp, currentIp);
                addOperationLog("warn", "[DDNS] " + task.getFullDomain() + " DNS解析IP(" + dnsIp + ")与本地IP(" + currentIp + ")不一致，执行更新");
            }
            
            // 更新DNS记录
            if ("腾讯云".equals(task.getProvider())) {
                updateTencentDns(task, currentIp);
            } else if ("阿里云".equals(task.getProvider())) {
                updateAliyunDns(task, currentIp);
            } else {
                throw new Exception("暂不支持 " + task.getProvider());
            }
            
            // 更新任务状态
            task.setLastIp(currentIp);
            task.setLastUpdateTime(LocalDateTime.now().format(formatter));
            task.setStatus("running");
            task.setLastError(null);
            saveTasks();
            
            log.info("DDNS更新成功: {} -> {}", task.getFullDomain(), currentIp);
            addOperationLog("success", "[DDNS] " + task.getFullDomain() + " 更新成功: " + currentIp);
            result.put("success", true);
            result.put("message", "更新成功");
            result.put("ip", currentIp);
            
        } catch (Exception e) {
            task.setStatus("error");
            task.setLastError(e.getMessage());
            task.setLastUpdateTime(LocalDateTime.now().format(formatter));
            saveTasks();
            
            log.error("DDNS更新失败: {} - {}", task.getFullDomain(), e.getMessage());
            addOperationLog("error", "[DDNS] " + task.getFullDomain() + " 更新失败: " + e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 解析域名获取当前DNS记录的IP
     */
    private String resolveDomainIp(String domain) {
        try {
            java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(domain);
            if (addresses.length > 0) {
                String ip = addresses[0].getHostAddress();
                // 只返回IPv4地址
                if (ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                    return ip;
                }
            }
        } catch (Exception e) {
            log.debug("解析域名IP失败: {} - {}", domain, e.getMessage());
        }
        return null;
    }
    
    /**
     * 获取IP（带自动切换备用服务）
     * 优先使用用户选择的服务，失败则依次尝试内置服务
     */
    private String fetchIPWithFallback(DdnsTask task) {
        // 1. 优先尝试用户选择的服务
        String ip = fetchIP(task.getIpServiceUrl());
        if (ip != null) {
            return ip;
        }
        
        log.warn("首选IP服务不可用: {}，尝试备用服务", task.getIpServiceName());
        addOperationLog("warn", "[DDNS] " + task.getFullDomain() + " 首选服务 " + task.getIpServiceName() + " 不可用，切换备用服务");
        
        // 2. 依次尝试内置服务
        for (IP_SERVICES service : IP_SERVICES.values()) {
            // 跳过已尝试的服务
            if (service.getUrl().equals(task.getIpServiceUrl())) {
                continue;
            }
            
            ip = fetchIP(service.getUrl());
            if (ip != null) {
                log.info("使用备用服务获取IP成功: {} -> {}", service.name(), ip);
                addOperationLog("info", "[DDNS] " + task.getFullDomain() + " 使用备用服务 " + service.name() + " 获取IP: " + ip);
                return ip;
            }
        }
        
        return null;
    }
    
    /**
     * 添加操作日志
     */
    private void addOperationLog(String type, String message) {
        Map<String, String> logEntry = new HashMap<>();
        logEntry.put("time", LocalDateTime.now().format(formatter));
        logEntry.put("type", type);
        logEntry.put("message", message);
        operationLogs.add(logEntry);
        
        // 保持日志数量不超过上限
        while (operationLogs.size() > MAX_LOGS) {
            operationLogs.remove(0);
        }
    }
    
    /**
     * 获取操作日志（从指定索引开始）
     */
    public List<Map<String, String>> getOperationLogs(int fromIndex) {
        if (fromIndex >= operationLogs.size()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(operationLogs.subList(fromIndex, operationLogs.size()));
    }
    
    /**
     * 获取当前日志数量
     */
    public int getLogCount() {
        return operationLogs.size();
    }
    
    /**
     * 更新阿里云DNS
     */
    private void updateAliyunDns(DdnsTask task, String ip) throws Exception {
        com.aliyun.alidns20150109.Client client = createAliyunClient(task.getSecretId(), task.getSecretKey());
        
        // 查找已存在的记录
        String recordId = null;
        try {
            com.aliyun.alidns20150109.models.DescribeDomainRecordsRequest listReq = 
                new com.aliyun.alidns20150109.models.DescribeDomainRecordsRequest()
                    .setDomainName(task.getDomain())
                    .setRRKeyWord(task.getSubdomain())
                    .setType("A");
            com.aliyun.alidns20150109.models.DescribeDomainRecordsResponse listResp = client.describeDomainRecords(listReq);
            
            if (listResp.getBody().getDomainRecords() != null && 
                listResp.getBody().getDomainRecords().getRecord() != null) {
                for (var record : listResp.getBody().getDomainRecords().getRecord()) {
                    if (task.getSubdomain().equals(record.getRR()) && "A".equals(record.getType())) {
                        recordId = record.getRecordId();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("查询阿里云DNS记录失败: {}", e.getMessage());
        }
        
        if (recordId != null) {
            // 更新记录
            com.aliyun.alidns20150109.models.UpdateDomainRecordRequest updateReq = 
                new com.aliyun.alidns20150109.models.UpdateDomainRecordRequest()
                    .setRecordId(recordId)
                    .setRR(task.getSubdomain())
                    .setType("A")
                    .setValue(ip);
            client.updateDomainRecord(updateReq);
        } else {
            // 创建记录
            com.aliyun.alidns20150109.models.AddDomainRecordRequest addReq = 
                new com.aliyun.alidns20150109.models.AddDomainRecordRequest()
                    .setDomainName(task.getDomain())
                    .setRR(task.getSubdomain())
                    .setType("A")
                    .setValue(ip);
            client.addDomainRecord(addReq);
        }
    }
    
    /**
     * 创建阿里云DNS客户端
     */
    private com.aliyun.alidns20150109.Client createAliyunClient(String accessKeyId, String accessKeySecret) throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
            .setAccessKeyId(accessKeyId)
            .setAccessKeySecret(accessKeySecret)
            .setEndpoint("alidns.cn-hangzhou.aliyuncs.com");
        return new com.aliyun.alidns20150109.Client(config);
    }
    
    /**
     * 更新腾讯云DNS
     */
    private void updateTencentDns(DdnsTask task, String ip) throws Exception {
        Credential cred = new Credential(task.getSecretId(), task.getSecretKey());
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("dnspod.tencentcloudapi.com");
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        DnspodClient client = new DnspodClient(cred, "", clientProfile);
        
        // 查找已存在的记录
        Long recordId = null;
        try {
            DescribeRecordListRequest listReq = new DescribeRecordListRequest();
            listReq.setDomain(task.getDomain());
            listReq.setSubdomain(task.getSubdomain());
            DescribeRecordListResponse listResp = client.DescribeRecordList(listReq);
            
            if (listResp.getRecordList() != null && listResp.getRecordList().length > 0) {
                for (RecordListItem record : listResp.getRecordList()) {
                    if ("A".equals(record.getType())) {
                        recordId = record.getRecordId();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // 记录不存在
        }
        
        if (recordId != null) {
            // 更新记录
            ModifyRecordRequest modifyReq = new ModifyRecordRequest();
            modifyReq.setDomain(task.getDomain());
            modifyReq.setRecordId(recordId);
            modifyReq.setSubDomain(task.getSubdomain());
            modifyReq.setRecordType("A");
            modifyReq.setRecordLine("默认");
            modifyReq.setValue(ip);
            client.ModifyRecord(modifyReq);
        } else {
            // 创建记录
            CreateRecordRequest createReq = new CreateRecordRequest();
            createReq.setDomain(task.getDomain());
            createReq.setSubDomain(task.getSubdomain());
            createReq.setRecordType("A");
            createReq.setRecordLine("默认");
            createReq.setValue(ip);
            client.CreateRecord(createReq);
        }
    }
    
    /**
     * 从URL获取IP
     */
    private String fetchIP(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                return extractIPv4(response.toString());
            }
        } catch (Exception e) {
            log.debug("获取IP失败: {} - {}", urlStr, e.getMessage());
        }
        return null;
    }
    
    /**
     * 从响应内容中提取IPv4地址
     */
    private String extractIPv4(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        // 使用正则匹配IPv4地址
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String ip = matcher.group(1);
            // 验证IP格式有效性
            String[] parts = ip.split("\\.");
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return null;
                }
            }
            return ip;
        }
        return null;
    }
    
    /**
     * 构建完整域名
     */
    private String buildFullDomain(String subdomain, String domain) {
        if ("@".equals(subdomain) || subdomain == null || subdomain.isEmpty()) {
            return domain;
        }
        return subdomain + "." + domain;
    }
    
    /**
     * 加载任务
     */
    private void loadTasks() {
        File file = new File(TASKS_FILE);
        if (!file.exists()) {
            return;
        }
        
        try (Reader reader = new FileReader(file)) {
            List<DdnsTask> list = gson.fromJson(reader, new TypeToken<List<DdnsTask>>(){}.getType());
            if (list != null) {
                list.forEach(t -> tasks.put(t.getId(), t));
            }
        } catch (Exception e) {
            log.error("加载DDNS任务失败", e);
        }
    }
    
    /**
     * 保存任务
     */
    private void saveTasks() {
        try (Writer writer = new FileWriter(TASKS_FILE)) {
            gson.toJson(new ArrayList<>(tasks.values()), writer);
        } catch (Exception e) {
            log.error("保存DDNS任务失败", e);
        }
    }
}
