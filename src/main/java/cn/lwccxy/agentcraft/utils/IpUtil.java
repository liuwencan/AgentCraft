package cn.lwccxy.agentcraft.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 客户端信息提取工具（IP / User-Agent）
 */
public final class IpUtil {

    private static final String UNKNOWN = "unknown";

    private IpUtil() {
    }

    /**
     * 获取真实客户端 IP，兼容常见反向代理头
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP"
        };
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能是多级代理，取第一个非 unknown
                int comma = ip.indexOf(',');
                if (comma > 0) {
                    ip = ip.substring(0, comma);
                }
                return ip.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 获取并截断 User-Agent（作为登录设备记录，最长 100）
     */
    public static String getDevice(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String ua = request.getHeader("User-Agent");
        if (!StringUtils.hasText(ua)) {
            return UNKNOWN;
        }
        return ua.length() > 100 ? ua.substring(0, 100) : ua;
    }
}
