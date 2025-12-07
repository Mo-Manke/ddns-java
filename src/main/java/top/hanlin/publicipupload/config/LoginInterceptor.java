package top.hanlin.publicipupload.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    private static final String SESSION_USER = "loggedIn";
    private static final String COOKIE_SESSION = "DDNS_SESSION";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        
        // 检查是否已登录
        if (isLoggedIn(request)) {
            return true;
        }
        
        log.info("未登录访问: {} , 重定向到登录页", uri);
        
        // 判断是否是AJAX请求
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        if ("XMLHttpRequest".equals(requestedWith) || 
            (accept != null && accept.contains("application/json"))) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
            return false;
        }
        
        // 普通请求重定向到登录页
        response.sendRedirect("/");
        return false;
    }

    private boolean isLoggedIn(HttpServletRequest request) {
        // 检查Session（优先，适用于不保持登录的情况）
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_USER))) {
            return true;
        }
        
        // 检查Cookie（适用于保持登录的情况）
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_SESSION.equals(cookie.getName()) && "valid".equals(cookie.getValue())) {
                    // Cookie有效，恢复Session
                    request.getSession().setAttribute(SESSION_USER, true);
                    return true;
                }
            }
        }
        
        return false;
    }
}
