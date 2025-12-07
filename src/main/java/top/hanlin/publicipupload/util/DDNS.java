package top.hanlin.publicipupload.util;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class DDNS {

    // 自定义IP服务列表（从文件加载）
    private static List<String> customServices = new ArrayList<>();
    private static final String CUSTOM_SERVICES_FILE = "ip_services.txt";
    
    // 本地网卡监控列表 (格式: interfaceName|ipType)
    private static List<String> localInterfaceMonitors = new ArrayList<>();
    private static final String LOCAL_INTERFACES_FILE = "local_interfaces.txt";

    static {
        loadCustomServices();
        loadLocalInterfaceMonitors();
    }

    /**
     * 加载自定义IP服务列表
     */
    public static void loadCustomServices() {
        customServices.clear();
        File file = new File(CUSTOM_SERVICES_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        customServices.add(line);
                    }
                }
            } catch (IOException e) {
                System.err.println("加载自定义IP服务失败: " + e.getMessage());
            }
        }
    }

    /**
     * 加载本地网卡监控列表
     */
    public static void loadLocalInterfaceMonitors() {
        localInterfaceMonitors.clear();
        File file = new File(LOCAL_INTERFACES_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        localInterfaceMonitors.add(line);
                    }
                }
            } catch (IOException e) {
                System.err.println("加载本地网卡监控失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 添加本地网卡监控
     */
    public static boolean addLocalInterfaceMonitor(String interfaceName, String ipType) {
        if (interfaceName == null || interfaceName.trim().isEmpty()) {
            return false;
        }
        String entry = interfaceName.trim() + "|" + ipType;
        
        // 检查是否已存在
        if (localInterfaceMonitors.contains(entry)) {
            return false;
        }
        
        // 添加到列表
        localInterfaceMonitors.add(entry);
        
        // 保存到文件
        try (FileWriter writer = new FileWriter(LOCAL_INTERFACES_FILE, true)) {
            writer.write(entry + "\n");
            return true;
        } catch (IOException e) {
            System.err.println("保存本地网卡监控失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 添加自定义IP服务
     */
    public static boolean addCustomService(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        url = url.trim();
        
        // 检查是否已存在
        if (customServices.contains(url)) {
            return false;
        }
        
        // 添加到列表
        customServices.add(url);
        
        // 保存到文件
        try (FileWriter writer = new FileWriter(CUSTOM_SERVICES_FILE, true)) {
            writer.write(url + "\n");
            return true;
        } catch (IOException e) {
            System.err.println("保存自定义IP服务失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取所有IP服务列表（内置+自定义）
     */
    public static List<Map<String, String>> getAllServices() {
        List<Map<String, String>> services = new ArrayList<>();
        
        // 添加内置服务
        for (IP_SERVICES service : IP_SERVICES.values()) {
            Map<String, String> item = new HashMap<>();
            item.put("name", service.getName());
            item.put("url", service.getUrl());
            item.put("type", "builtin");
            item.put("ipType", service.getType()); // ipv4 或 ipv6
            services.add(item);
        }
        
        // 添加自定义服务
        loadCustomServices(); // 重新加载
        for (String url : customServices) {
            Map<String, String> item = new HashMap<>();
            item.put("name", "自定义");
            item.put("url", url);
            item.put("type", "custom");
            item.put("ipType", "ipv4"); // 自定义默认为IPv4
            services.add(item);
        }
        
        return services;
    }

    /**
     * 从所有服务获取IP（并行请求）
     */
    public static List<Map<String, String>> getAllPublicIPs() {
        List<Map<String, String>> results = new ArrayList<>();
        List<Map<String, String>> services = getAllServices();
        
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(services.size(), 10));
        List<Future<Map<String, String>>> futures = new ArrayList<>();
        
        for (Map<String, String> service : services) {
            futures.add(executor.submit(() -> {
                Map<String, String> result = new HashMap<>();
                result.put("name", service.get("name"));
                result.put("url", service.get("url"));
                result.put("type", service.get("type"));
                result.put("ipType", service.get("ipType"));
                
                try {
                    boolean isIPv6 = "ipv6".equals(service.get("ipType"));
                    String ip = fetchIP(service.get("url"), 5000, isIPv6);
                    result.put("ip", ip != null ? ip.trim() : "");
                    result.put("status", ip != null && !ip.isEmpty() ? "success" : "failed");
                } catch (Exception e) {
                    result.put("ip", "");
                    result.put("status", "failed");
                    result.put("error", e.getMessage());
                }
                return result;
            }));
        }
        
        for (Future<Map<String, String>> future : futures) {
            try {
                results.add(future.get(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                // 超时或异常
            }
        }
        
        executor.shutdown();
        
        // 添加本地网卡IPv6地址（自动检测）
        results.addAll(getLocalIPv6Addresses());
        
        // 添加用户配置的本地网卡监控
        loadLocalInterfaceMonitors();
        results.addAll(getMonitoredInterfaceIPs());
        
        return results;
    }
    
    /**
     * 只获取IPv4服务结果
     */
    public static List<Map<String, String>> getIPv4Only() {
        List<Map<String, String>> results = new ArrayList<>();
        List<Map<String, String>> services = getAllServices();
        
        // 过滤只保留IPv4服务
        List<Map<String, String>> ipv4Services = new ArrayList<>();
        for (Map<String, String> s : services) {
            if ("ipv4".equals(s.get("ipType"))) {
                ipv4Services.add(s);
            }
        }
        
        if (ipv4Services.isEmpty()) return results;
        
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(ipv4Services.size(), 10));
        List<Future<Map<String, String>>> futures = new ArrayList<>();
        
        for (Map<String, String> service : ipv4Services) {
            futures.add(executor.submit(() -> {
                Map<String, String> result = new HashMap<>();
                result.put("name", service.get("name"));
                result.put("url", service.get("url"));
                result.put("type", service.get("type"));
                result.put("ipType", "ipv4");
                
                try {
                    String ip = fetchIP(service.get("url"), 5000, false);
                    result.put("ip", ip != null ? ip.trim() : "");
                    result.put("status", ip != null && !ip.isEmpty() ? "success" : "failed");
                } catch (Exception e) {
                    result.put("ip", "");
                    result.put("status", "failed");
                }
                return result;
            }));
        }
        
        for (Future<Map<String, String>> future : futures) {
            try {
                results.add(future.get(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                // 超时或异常
            }
        }
        
        executor.shutdown();
        
        // 添加用户监控的IPv4本地网卡
        loadLocalInterfaceMonitors();
        for (Map<String, String> m : getMonitoredInterfaceIPs()) {
            if ("ipv4".equals(m.get("ipType"))) {
                results.add(m);
            }
        }
        
        return results;
    }
    
    /**
     * 只获取IPv6服务结果
     */
    public static List<Map<String, String>> getIPv6Only() {
        List<Map<String, String>> results = new ArrayList<>();
        List<Map<String, String>> services = getAllServices();
        
        // 过滤只保留IPv6服务
        List<Map<String, String>> ipv6Services = new ArrayList<>();
        for (Map<String, String> s : services) {
            if ("ipv6".equals(s.get("ipType"))) {
                ipv6Services.add(s);
            }
        }
        
        ExecutorService executor = null;
        if (!ipv6Services.isEmpty()) {
            executor = Executors.newFixedThreadPool(Math.min(ipv6Services.size(), 10));
            List<Future<Map<String, String>>> futures = new ArrayList<>();
            
            for (Map<String, String> service : ipv6Services) {
                futures.add(executor.submit(() -> {
                    Map<String, String> result = new HashMap<>();
                    result.put("name", service.get("name"));
                    result.put("url", service.get("url"));
                    result.put("type", service.get("type"));
                    result.put("ipType", "ipv6");
                    
                    try {
                        String ip = fetchIP(service.get("url"), 5000, true);
                        result.put("ip", ip != null ? ip.trim() : "");
                        result.put("status", ip != null && !ip.isEmpty() ? "success" : "failed");
                    } catch (Exception e) {
                        result.put("ip", "");
                        result.put("status", "failed");
                    }
                    return result;
                }));
            }
            
            for (Future<Map<String, String>> future : futures) {
                try {
                    results.add(future.get(10, TimeUnit.SECONDS));
                } catch (Exception e) {
                    // 超时或异常
                }
            }
            
            executor.shutdown();
        }
        
        // 添加本地网卡IPv6地址（自动检测）
        results.addAll(getLocalIPv6Addresses());
        
        // 添加用户监控的IPv6本地网卡
        loadLocalInterfaceMonitors();
        for (Map<String, String> m : getMonitoredInterfaceIPs()) {
            if ("ipv6".equals(m.get("ipType"))) {
                results.add(m);
            }
        }
        
        return results;
    }
    
    /**
     * 获取用户监控的本地网卡IP
     */
    private static List<Map<String, String>> getMonitoredInterfaceIPs() {
        List<Map<String, String>> results = new ArrayList<>();
        
        for (String entry : localInterfaceMonitors) {
            String[] parts = entry.split("\\|");
            if (parts.length != 2) continue;
            
            String interfaceName = parts[0];
            String ipType = parts[1];
            
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (!ni.getDisplayName().equals(interfaceName)) continue;
                    if (ni.isLoopback() || !ni.isUp()) continue;
                    
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        
                        if ("ipv4".equals(ipType) && addr instanceof Inet4Address) {
                            Map<String, String> item = new HashMap<>();
                            item.put("name", interfaceName + " (监控)");
                            item.put("ip", addr.getHostAddress());
                            item.put("type", "local");
                            item.put("ipType", "ipv4");
                            item.put("status", "success");
                            item.put("url", "本地网卡");
                            results.add(item);
                        } else if ("ipv6".equals(ipType) && addr instanceof Inet6Address) {
                            String ip = addr.getHostAddress();
                            int scopeIdx = ip.indexOf('%');
                            if (scopeIdx > 0) {
                                ip = ip.substring(0, scopeIdx);
                            }
                            if (ip.toLowerCase().startsWith("fe80:")) continue;
                            
                            // 检查是否已在自动检测的结果中
                            Map<String, String> item = new HashMap<>();
                            item.put("name", interfaceName + " (监控)");
                            item.put("ip", ip);
                            item.put("type", "local");
                            item.put("ipType", "ipv6");
                            item.put("status", "success");
                            item.put("url", "本地网卡");
                            results.add(item);
                        }
                    }
                }
            } catch (SocketException e) {
                // 忽略
            }
        }
        
        return results;
    }

    /**
     * 从指定URL获取IP
     */
    private static String fetchIP(String urlStr, int timeout, boolean isIPv6) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "curl/7.64.1");
            conn.setRequestProperty("Accept", "text/plain");
            
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                String content = response.toString().trim();
                // 先尝试直接返回（如果是纯IP）
                if (isIPv6) {
                    String ip = extractIPv6(content);
                    // 如果提取失败，尝试直接使用内容（某些服务直接返回纯IP）
                    if (ip == null && content.contains(":") && !content.contains("<")) {
                        return content.trim();
                    }
                    return ip;
                } else {
                    return extractIPv4(content);
                }
            }
        } catch (Exception e) {
            System.err.println("获取IP失败 [" + urlStr + "]: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 从响应内容中提取IPv4地址
     */
    private static String extractIPv4(String content) {
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
     * 从响应内容中提取IPv6地址
     */
    private static String extractIPv6(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        // IPv6地址正则：匹配标准IPv6格式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|" +  // 完整格式
            "([0-9a-fA-F]{1,4}:){1,7}:|" +                // 末尾省略
            "([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|" +
            "([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|" +
            "([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|" +
            "([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|" +
            "([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|" +
            "[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|" +
            ":((:[0-9a-fA-F]{1,4}){1,7}|:)"              // ::开头
        );
        java.util.regex.Matcher matcher = pattern.matcher(content.trim());
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    /**
     * 获取单个公网IP（使用第一个成功的服务）
     */
    public static String getPublicIP() {
        List<Map<String, String>> services = getAllServices();
        
        for (Map<String, String> service : services) {
            if ("ipv4".equals(service.get("ipType"))) {
                String ip = fetchIP(service.get("url"), 5000, false);
                if (ip != null && !ip.isEmpty()) {
                    return ip;
                }
            }
        }
        return "";
    }
    
    /**
     * 获取单个公网IPv6（使用第一个成功的服务）
     */
    public static String getPublicIPv6() {
        List<Map<String, String>> services = getAllServices();
        
        for (Map<String, String> service : services) {
            if ("ipv6".equals(service.get("ipType"))) {
                String ip = fetchIP(service.get("url"), 5000, true);
                if (ip != null && !ip.isEmpty()) {
                    return ip;
                }
            }
        }
        return "";
    }
    
    /**
     * 获取本地所有网卡的IPv6地址
     */
    public static List<Map<String, String>> getLocalIPv6Addresses() {
        List<Map<String, String>> results = new ArrayList<>();
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                
                // 跳过回环接口和未启用的接口
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                
                String interfaceName = ni.getDisplayName();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    
                    // 只获取IPv6地址
                    if (addr instanceof Inet6Address) {
                        Inet6Address ipv6Addr = (Inet6Address) addr;
                        String ip = ipv6Addr.getHostAddress();
                        
                        // 移除作用域ID（如 %eth0）
                        int scopeIdx = ip.indexOf('%');
                        if (scopeIdx > 0) {
                            ip = ip.substring(0, scopeIdx);
                        }
                        
                        // 跳过链路本地地址（fe80::开头）
                        if (ip.toLowerCase().startsWith("fe80:")) {
                            continue;
                        }
                        
                        Map<String, String> item = new HashMap<>();
                        item.put("name", interfaceName);
                        item.put("ip", ip);
                        item.put("type", "local");
                        item.put("ipType", "ipv6");
                        item.put("status", "success");
                        item.put("url", "本地网卡");
                        results.add(item);
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("获取本地IPv6地址失败: " + e.getMessage());
        }
        
        return results;
    }
    
    /**
     * 获取本地网卡列表（包含IPv4和IPv6地址）
     */
    public static List<Map<String, Object>> getNetworkInterfaces() {
        List<Map<String, Object>> results = new ArrayList<>();
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                
                // 跳过回环接口和未启用的接口
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                
                Map<String, Object> item = new HashMap<>();
                item.put("name", ni.getDisplayName());
                item.put("displayName", ni.getName());
                
                List<String> ipv4List = new ArrayList<>();
                List<String> ipv6List = new ArrayList<>();
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    
                    if (addr instanceof Inet4Address) {
                        ipv4List.add(addr.getHostAddress());
                    } else if (addr instanceof Inet6Address) {
                        String ip = addr.getHostAddress();
                        // 移除作用域ID
                        int scopeIdx = ip.indexOf('%');
                        if (scopeIdx > 0) {
                            ip = ip.substring(0, scopeIdx);
                        }
                        // 跳过链路本地地址
                        if (!ip.toLowerCase().startsWith("fe80:")) {
                            ipv6List.add(ip);
                        }
                    }
                }
                
                item.put("ipv4", ipv4List);
                item.put("ipv6", ipv6List);
                
                // 只添加有IP地址的网卡
                if (!ipv4List.isEmpty() || !ipv6List.isEmpty()) {
                    results.add(item);
                }
            }
        } catch (SocketException e) {
            System.err.println("获取网卡列表失败: " + e.getMessage());
        }
        
        return results;
    }
}
