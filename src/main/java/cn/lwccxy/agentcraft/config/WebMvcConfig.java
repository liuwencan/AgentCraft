package cn.lwccxy.agentcraft.config;

import cn.lwccxy.agentcraft.UserInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置：注册用户登录拦截器并定义拦截范围。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserInterceptor userInterceptor;

    public WebMvcConfig(UserInterceptor userInterceptor) {
        this.userInterceptor = userInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userInterceptor)
                // 拦截所有路径，由 UserInterceptor.isExclude() 内部再细化白名单
                .addPathPatterns("/**")
                // 不拦截的扩展路径：静态资源、SSE 流式接口
                .excludePathPatterns(
                        "/favicon.ico",
                        "/static/**",
                        "/css/**",
                        "/js/**",
                        "/img/**",
                        "/fonts/**",
                        "/webjars/**"
                );
    }
}
