package top.hanlin.publicipupload.util;

public enum IP_SERVICES {

    // ========== IPv4 服务 ==========
    // 国外服务
    AMAZON("http://checkip.amazonaws.com", "Amazon", "ipv4"),
    ICANHAZIP("https://ipv4.icanhazip.com", "icanhazip", "ipv4"),
    IFCONFIG("https://ifconfig.me/ip", "ifconfig.me", "ipv4"),
    IPINFO("https://ipinfo.io/ip", "ipinfo.io", "ipv4"),
    IDENT("https://v4.ident.me", "ident.me", "ipv4"),
    WTFISMYIP("https://wtfismyip.com/text", "wtfismyip", "ipv4"),
    // 国内服务
    IPIP("https://myip.ipip.net/ip", "ipip.net", "ipv4"),

    // ========== IPv6 服务 ==========
    ICANHAZIP_V6("https://ipv6.icanhazip.com", "icanhazip-v6", "ipv6"),
    IDENT_V6("https://v6.ident.me", "ident.me-v6", "ipv6"),
    IFCONFIG_V6("https://ifconfig.co/ip", "ifconfig.co-v6", "ipv6"),
    IP_SB_V6("https://api-ipv6.ip.sb/ip", "ip.sb-v6", "ipv6"),
    TEST_IPV6("https://v6.ipv6-test.com/api/myip.php", "ipv6-test", "ipv6");

    private final String url;
    private final String name;
    private final String type; // ipv4 或 ipv6

    IP_SERVICES(String url, String name, String type) {
        this.url = url;
        this.name = name;
        this.type = type;
    }

    public String getUrl() {
        return url;
    }
    
    public String getName() {
        return name;
    }
    
    public String getType() {
        return type;
    }
    
    public boolean isIPv6() {
        return "ipv6".equals(type);
    }
}
