package top.hanlin.publicipupload.util;

public enum IP_SERVICES {

    // 国外服务
    AMAZON("http://checkip.amazonaws.com", "Amazon"),
    ICANHAZIP("https://icanhazip.com", "icanhazip"),
    IFCONFIG("https://ifconfig.me/ip", "ifconfig.me"),
    IPINFO("https://ipinfo.io/ip", "ipinfo.io"),

    IDENT("https://ident.me", "ident.me"),
    WTFISMYIP("https://wtfismyip.com/text", "wtfismyip"),
    
    // 国内服务

    IPIP("https://myip.ipip.net/ip", "ipip.net");




    private final String url;
    private final String name;

    IP_SERVICES(String url, String name) {
        this.url = url;
        this.name = name;
    }

    public String getUrl() {
        return url;
    }
    
    public String getName() {
        return name;
    }
}
