package top.hanlin.publicipupload.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DDNS定时解析任务
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DdnsTask {
    private String id;              // 任务ID
    private String provider;        // 云服务商 (腾讯云/阿里云)
    private String secretId;        // SecretId
    private String secretKey;       // SecretKey
    private String domain;          // 根域名
    private String subdomain;       // 子域名
    private String fullDomain;      // 完整域名 (subdomain.domain)
    private String ipServiceUrl;    // 使用的IP服务URL
    private String ipServiceName;   // 使用的IP服务名称
    private int interval;           // 定时间隔(秒)
    private boolean enabled;        // 是否启用
    private String lastIp;          // 上次解析的IP
    private String lastUpdateTime;  // 上次更新时间
    private String status;          // 状态: running, stopped, error
    private String lastError;       // 上次错误信息
}
