package top.hanlin.publicipupload.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

public class DDNS {

    // 自定义IP服务列表（从文件加载）
    private static List<String> customServices = new ArrayList<>();
    private static final String CUSTOM_SERVICES_FILE = "ip_services.txt";

    static {
        loadCustomServices();
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
            services.add(item);
        }
        
        // 添加自定义服务
        loadCustomServices(); // 重新加载
        for (String url : customServices) {
            Map<String, String> item = new HashMap<>();
            item.put("name", "自定义");
            item.put("url", url);
            item.put("type", "custom");
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
        
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(services.size(), 5));
        List<Future<Map<String, String>>> futures = new ArrayList<>();
        
        for (Map<String, String> service : services) {
            futures.add(executor.submit(() -> {
                Map<String, String> result = new HashMap<>();
                result.put("name", service.get("name"));
                result.put("url", service.get("url"));
                result.put("type", service.get("type"));
                
                try {
                    String ip = fetchIP(service.get("url"), 5000);
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
        return results;
    }

    /**
     * 从指定URL获取IP
     */
    private static String fetchIP(String urlStr, int timeout) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
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
            // 忽略
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
     * 获取单个公网IP（使用第一个成功的服务）
     */
    public static String getPublicIP() {
        List<Map<String, String>> services = getAllServices();
        
        for (Map<String, String> service : services) {
            String ip = fetchIP(service.get("url"), 5000);
            if (ip != null && !ip.isEmpty()) {
                return ip;
            }
        }
        return "";
    }
}
