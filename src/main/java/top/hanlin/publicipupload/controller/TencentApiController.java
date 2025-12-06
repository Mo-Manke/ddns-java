package top.hanlin.publicipupload.controller;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.dnspod.v20210323.DnspodClient;
import com.tencentcloudapi.dnspod.v20210323.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.hanlin.publicipupload.entity.DdnsTask;
import top.hanlin.publicipupload.model.ApiResponse;
import top.hanlin.publicipupload.service.DdnsTaskService;
import top.hanlin.publicipupload.util.DDNS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dns")
public class TencentApiController {

    @Autowired
    private DdnsTaskService ddnsTaskService;

    /**
     * 创建腾讯云 DnspodClient
     */
    private DnspodClient createDnspodClient(String id, String key) {
        Credential cred = new Credential(id, key);
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("dnspod.tencentcloudapi.com");
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        return new DnspodClient(cred, "", clientProfile);
    }

    /**
     * 获取账号下所有根域名列表
     */
    @PostMapping("/domainList")
    public Object getDomainList(
            @RequestParam String id, 
            @RequestParam String key,
            @RequestParam(defaultValue = "腾讯云") String provider) {
        log.info("获取域名列表 provider={} id={}", provider, id);
        
        try {
            List<String> domainNames = new ArrayList<>();
            
            if ("阿里云".equals(provider)) {
                domainNames = getAliyunDomainList(id, key);
            } else {
                // 默认腾讯云
                DnspodClient client = createDnspodClient(id, key);
                DescribeDomainListRequest req = new DescribeDomainListRequest();
                DescribeDomainListResponse resp = client.DescribeDomainList(req);
                
                if (resp.getDomainList() != null) {
                    for (DomainListItem item : resp.getDomainList()) {
                        domainNames.add(item.getName());
                    }
                }
            }
            return ApiResponse.success(domainNames);
        } catch (Exception e) {
            log.error("获取域名列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 获取阿里云域名列表
     */
    private List<String> getAliyunDomainList(String accessKeyId, String accessKeySecret) throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
            .setAccessKeyId(accessKeyId)
            .setAccessKeySecret(accessKeySecret)
            .setEndpoint("alidns.cn-hangzhou.aliyuncs.com");
        com.aliyun.alidns20150109.Client client = new com.aliyun.alidns20150109.Client(config);
        
        com.aliyun.alidns20150109.models.DescribeDomainsRequest req = 
            new com.aliyun.alidns20150109.models.DescribeDomainsRequest();
        com.aliyun.alidns20150109.models.DescribeDomainsResponse resp = client.describeDomains(req);
        
        List<String> domainNames = new ArrayList<>();
        if (resp.getBody().getDomains() != null && resp.getBody().getDomains().getDomain() != null) {
            for (var domain : resp.getBody().getDomains().getDomain()) {
                domainNames.add(domain.getDomainName());
            }
        }
        return domainNames;
    }

    /**
     * 获取所有IP服务的结果
     */
    @GetMapping("/allIps")
    public Object getAllIps() {
        log.info("获取所有IP服务结果");
        List<Map<String, String>> results = DDNS.getAllPublicIPs();
        return ApiResponse.success(results);
    }

    /**
     * 添加自定义IP服务
     */
    @PostMapping("/addIpService")
    public Object addIpService(@RequestParam String url) {
        log.info("添加自定义IP服务: {}", url);
        if (DDNS.addCustomService(url)) {
            return ApiResponse.success("添加成功");
        }
        return ApiResponse.error("添加失败，可能已存在或URL无效");
    }

    /**
     * 创建或更新解析记录
     * @param id 腾讯云SecretId
     * @param key 腾讯云SecretKey
     * @param domain 根域名 (如: example.com)
     * @param subdomain 子域名/主机记录 (如: www, @, test)
     * @param ip 用户选择的IP地址
     */
    @PostMapping("/createOrUpdateRecord")
    public Object createOrUpdateRecord(
            @RequestParam String id,
            @RequestParam String key,
            @RequestParam String domain,
            @RequestParam String subdomain,
            @RequestParam String ip) {
        
        log.info("创建或更新解析记录: domain={}, subdomain={}, ip={}", domain, subdomain, ip);
        
        // 验证IP格式
        if (ip == null || !ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            return ApiResponse.error("IP地址格式无效");
        }
        String currentIp = ip.trim();

        try {
            DnspodClient client = createDnspodClient(id, key);
            
            // 先查询该子域名是否已存在解析记录
            Long existingRecordId = findExistingRecord(client, domain, subdomain);
            
            if (existingRecordId != null) {
                // 记录已存在，更新它
                return updateRecord(client, domain, existingRecordId, subdomain, currentIp);
            } else {
                // 记录不存在，创建新记录
                return createRecord(client, domain, subdomain, currentIp);
            }
        } catch (TencentCloudSDKException e) {
            log.error("操作解析记录失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 查找已存在的解析记录
     */
    private Long findExistingRecord(DnspodClient client, String domain, String subdomain) throws TencentCloudSDKException {
        try {
            DescribeRecordListRequest req = new DescribeRecordListRequest();
            req.setDomain(domain);
            req.setSubdomain(subdomain);
            req.setRecordType("A");
            
            DescribeRecordListResponse resp = client.DescribeRecordList(req);
            if (resp.getRecordList() != null && resp.getRecordList().length > 0) {
                return resp.getRecordList()[0].getRecordId();
            }
        } catch (TencentCloudSDKException e) {
            // 如果是记录不存在的错误，返回null
            if (e.getMessage().contains("记录列表为空") || e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
        return null;
    }

    /**
     * 创建新的解析记录
     */
    private Object createRecord(DnspodClient client, String domain, String subdomain, String ip) throws TencentCloudSDKException {
        CreateRecordRequest req = new CreateRecordRequest();
        req.setDomain(domain);
        req.setSubDomain(subdomain);
        req.setRecordType("A");
        req.setRecordLine("默认");
        req.setValue(ip);
        req.setTTL(600L);
        
        CreateRecordResponse resp = client.CreateRecord(req);
        log.info("创建解析记录成功: recordId={}", resp.getRecordId());
        return ApiResponse.success("创建成功: " + subdomain + "." + domain + " -> " + ip);
    }

    /**
     * 更新已存在的解析记录
     */
    private Object updateRecord(DnspodClient client, String domain, Long recordId, String subdomain, String ip) throws TencentCloudSDKException {
        ModifyRecordRequest req = new ModifyRecordRequest();
        req.setDomain(domain);
        req.setRecordId(recordId);
        req.setSubDomain(subdomain);
        req.setRecordType("A");
        req.setRecordLine("默认");
        req.setValue(ip);
        req.setTTL(600L);
        
        client.ModifyRecord(req);
        log.info("更新解析记录成功: recordId={}", recordId);
        return ApiResponse.success("更新成功: " + subdomain + "." + domain + " -> " + ip);
    }

    // ==================== DDNS 定时任务 API ====================

    /**
     * 获取所有DDNS任务
     */
    @GetMapping("/tasks")
    public Object getAllTasks() {
        return ApiResponse.success(ddnsTaskService.getAllTasks());
    }

    /**
     * 获取指定账号的DDNS任务
     */
    @GetMapping("/tasks/{secretId}")
    public Object getTasksByAccount(@PathVariable String secretId) {
        return ApiResponse.success(ddnsTaskService.getTasksByAccount(secretId));
    }

    /**
     * 添加DDNS任务
     */
    @PostMapping("/tasks")
    public Object addTask(
            @RequestParam String provider,
            @RequestParam String secretId,
            @RequestParam String secretKey,
            @RequestParam String domain,
            @RequestParam String subdomain,
            @RequestParam String ipServiceUrl,
            @RequestParam String ipServiceName,
            @RequestParam(defaultValue = "300") int interval) {
        
        DdnsTask task = new DdnsTask();
        task.setProvider(provider);
        task.setSecretId(secretId);
        task.setSecretKey(secretKey);
        task.setDomain(domain);
        task.setSubdomain(subdomain);
        task.setIpServiceUrl(ipServiceUrl);
        task.setIpServiceName(ipServiceName);
        task.setInterval(interval);
        
        DdnsTask created = ddnsTaskService.addTask(task);
        return ApiResponse.successData(created);
    }

    /**
     * 更新DDNS任务配置
     */
    @PutMapping("/tasks/{taskId}")
    public Object updateTask(
            @PathVariable String taskId,
            @RequestParam int interval,
            @RequestParam String ipServiceUrl,
            @RequestParam String ipServiceName) {
        
        DdnsTask updated = ddnsTaskService.updateTask(taskId, interval, ipServiceUrl, ipServiceName);
        if (updated != null) {
            return ApiResponse.successData(updated);
        }
        return ApiResponse.error("任务不存在");
    }

    /**
     * 启动DDNS任务
     */
    @PostMapping("/tasks/{taskId}/start")
    public Object startTask(@PathVariable String taskId) {
        if (ddnsTaskService.startTask(taskId)) {
            return ApiResponse.success("任务已启动");
        }
        return ApiResponse.error("启动失败");
    }

    /**
     * 停止DDNS任务
     */
    @PostMapping("/tasks/{taskId}/stop")
    public Object stopTask(@PathVariable String taskId) {
        if (ddnsTaskService.stopTask(taskId)) {
            return ApiResponse.success("任务已停止");
        }
        return ApiResponse.error("停止失败");
    }

    /**
     * 删除DDNS任务
     */
    @DeleteMapping("/tasks/{taskId}")
    public Object deleteTask(@PathVariable String taskId) {
        if (ddnsTaskService.deleteTask(taskId)) {
            return ApiResponse.success("任务已删除");
        }
        return ApiResponse.error("删除失败");
    }

    /**
     * 手动执行一次DDNS任务
     */
    @PostMapping("/tasks/{taskId}/execute")
    public Object executeTask(@PathVariable String taskId) {
        Map<String, Object> result = ddnsTaskService.executeTaskNow(taskId);
        if ((boolean) result.get("success")) {
            return ApiResponse.success(result.get("message") + (result.containsKey("ip") ? " IP: " + result.get("ip") : ""));
        }
        return ApiResponse.error((String) result.get("message"));
    }
    
    /**
     * 获取操作日志
     */
    @GetMapping("/logs")
    public Object getOperationLogs(@RequestParam(defaultValue = "0") int fromIndex) {
        return ApiResponse.successData(Map.of(
            "logs", ddnsTaskService.getOperationLogs(fromIndex),
            "total", ddnsTaskService.getLogCount()
        ));
    }
}
