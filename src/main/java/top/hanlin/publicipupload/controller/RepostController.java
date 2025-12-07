package top.hanlin.publicipupload.controller;


import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


import top.hanlin.publicipupload.entity.UserInfo;
import top.hanlin.publicipupload.service.RepostService;
import top.hanlin.publicipupload.service.TencentApiService;
import top.hanlin.publicipupload.service.impl.RepostServiceImpl;
import top.hanlin.publicipupload.service.impl.TencentApiServiceImpl;

import java.util.List;

@Slf4j
@Controller
public class RepostController {
    RepostService repostService = new RepostServiceImpl();
    TencentApiService tencentApiService = new TencentApiServiceImpl();
    private String status;
    private String error;
    
    private static final String SESSION_USER = "loggedIn";
    private static final String COOKIE_SESSION = "DDNS_SESSION";
    private static final int SESSION_TIMEOUT = 86400; // 1天（秒）

    /**
     * 检查是否已登录
     */
    private boolean isLoggedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_USER))) {
            return true;
        }
        // 检查Cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_SESSION.equals(cookie.getName()) && "valid".equals(cookie.getValue())) {
                    // Cookie有效，恢复Session
                    HttpSession newSession = request.getSession(true);
                    newSession.setAttribute(SESSION_USER, true);
                    newSession.setMaxInactiveInterval(SESSION_TIMEOUT);
                    return true;
                }
            }
        }
        return false;
    }

    @GetMapping("/lock")
    public String getLock(HttpServletRequest request, HttpServletResponse response) {
        log.info("退出登录");
        // 清除Session
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        // 清除Cookie
        Cookie cookie = new Cookie(COOKIE_SESSION, "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);
        return "redirect:/";
    }
    
    @PostMapping("/modify/password")
    public String modifyPassword(@RequestParam String password, String modify, 
                                  HttpServletRequest request, Model model) {
        if (!isLoggedIn(request)) {
            model.addAttribute("error", "请先登录");
            return "index";
        }
        
        // 检查是否是初始密码，只有初始密码才能修改
        if (!repostService.isInitialPassword()) {
            error = "密码已修改过，不支持再次修改";
            return "redirect:/pages/console";
        }
        
        log.info("修改密码");
        if (repostService.login(password)) {
            repostService.modifyPassword(modify);
            status = "密码修改成功";
        } else {
            error = "原密码不正确，修改失败";
        }

        return "redirect:/pages/console";
    }

    @GetMapping("/pages/console")
    public String console(Model model, HttpServletRequest request) {
        if (!isLoggedIn(request)) {
            model.addAttribute("error", "请登录");
            return "index";
        }
        
        // 检查是否需要强制修改密码
        boolean needChangePassword = repostService.isInitialPassword();
        
        List<UserInfo> allUser = repostService.getAllUser();
        model.addAttribute("status", status);
        model.addAttribute("error", error);
        model.addAttribute("users", allUser);
        model.addAttribute("needChangePassword", needChangePassword);
        status = null;
        error = null;
        return "pages/console";
    }

    @GetMapping("/")
    public String index(HttpServletRequest request) {
        // 如果已登录，直接跳转到控制台
        if (isLoggedIn(request)) {
            return "redirect:/pages/console";
        }
        return "index";
    }

    @PostMapping("/login")
    public String login(@RequestParam String password, Model model,
                        HttpServletRequest request, HttpServletResponse response) {
        log.info("用户登录");
        boolean results = repostService.login(password);
        if (results) {
            // 创建Session
            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_USER, true);
            session.setMaxInactiveInterval(SESSION_TIMEOUT);
            
            // 设置Cookie，1天过期
            Cookie cookie = new Cookie(COOKIE_SESSION, "valid");
            cookie.setMaxAge(SESSION_TIMEOUT);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            response.addCookie(cookie);
            
            status = "登录成功";
            return "redirect:/pages/console";
        } else {
            model.addAttribute("password", password);
            model.addAttribute("error", "密码错误");
            return "index";
        }
    }

    /**
     * 添加云服务账号
     * @param name 1=腾讯云, 2=阿里云
     * @param id   SecretId
     * @param key  SecretKey
     */
    @PostMapping("/addAccount")
    public String addAccount(@RequestParam String name, String id, String key, Model model) {
        log.info("添加云服务账号: provider={}, id={}", name, id);
        
        if (name == null || name.isEmpty()) {
            model.addAttribute("error", "请选择云服务商");
            model.addAttribute("users", repostService.getAllUser());
            return "pages/console";
        }
        
        String providerName = name.equals("1") ? "腾讯云" : "阿里云";
        
        if (name.equals("1")) {
            // 腾讯云：验证凭证
            if (tencentApiService.validateCredentials(id, key)) {
                if (tencentApiService.addIdAndKey(providerName, id, key)) {
                    status = "账号添加成功";
                } else {
                    error = "账号已存在或添加失败";
                }
            } else {
                model.addAttribute("error", "SecretId 或 SecretKey 无效，请检查");
                model.addAttribute("users", repostService.getAllUser());
                return "pages/console";
            }
        } else if (name.equals("2")) {
            // 阿里云：暂不支持验证，直接保存
            if (tencentApiService.addIdAndKey(providerName, id, key)) {
                status = "账号添加成功（阿里云暂不支持验证）";
            } else {
                error = "账号已存在或添加失败";
            }
        }
        
        return "redirect:/pages/console";
    }

    /**
     * 删除云服务账号
     */
    @PostMapping("/deleteAccount")
    public String deleteAccount(@RequestParam String provider, @RequestParam String id,
                                 HttpServletRequest request, Model model) {
        if (!isLoggedIn(request)) {
            model.addAttribute("error", "请先登录");
            return "index";
        }
        
        log.info("删除云服务账号: provider={}, id={}", provider, id);
        
        if (tencentApiService.deleteAccount(provider, id)) {
            status = "账号删除成功";
        } else {
            error = "账号删除失败";
        }
        
        return "redirect:/pages/console";
    }
}
