package top.hanlin.publicipupload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public LoginInterceptor loginInterceptor() {
        return new LoginInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor())
                .addPathPatterns("/**")  // 拦截所有请求
                .excludePathPatterns(
                        "/",              // 登录页
                        "/index",         // 登录页别名
                        "/login",         // 登录接口
                        "/static/**",     // 静态资源
                        "/css/**",
                        "/js/**",
                        "/img/**",
                        "/favicon.ico",
                        "/error",
                        "/webjars/**"
                );
    }
}
