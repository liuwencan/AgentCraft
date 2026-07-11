package cn.lwccxy.agentcraft;

import cn.dev33.satoken.stp.StpUtil;
import cn.lwccxy.agentcraft.common.BaseResult;
import cn.lwccxy.agentcraft.context.UserContext;
import cn.lwccxy.agentcraft.entity.User;
import cn.lwccxy.agentcraft.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;

/**
 * 全局用户登录拦截器
 * <ul>
 *   <li>已登录：把 users.id 写入 {@link UserContext} ThreadLocal，业务层可随时读取</li>
 *   <li>未登录：直接返回 {@code 401 + BaseResult} 统一响应体，前端 axios 拦截器据此跳登录</li>
 *   <li>请求结束后无论成功失败都清除 ThreadLocal，防止线程复用导致内存泄漏</li>
 * </ul>
 * 路径白名单（无需登录）：
 * <ul>
 *   <li>{@code /auth/send-code}    发送验证码</li>
 *   <li>{@code /auth/login}        登录入口</li>
 *   <li>{@code /auth/logout}       退出登录</li>
 *   <li>{@code /error}             错误页</li>
 * </ul>
 * 其余路径必须登录。
 */
@Component
public class UserInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UserInterceptor.class);

    /** 不需要登录校验的路径前缀集合 */
    public static final String[] EXCLUDE_PATHS = new String[]{
            "/auth/send-code",
            "/auth/login",
            "/auth/logout",
            "/error"
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserService userService;

    public UserInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (isExclude(path)) {
            return true;
        }

        // OPTIONS 预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (StpUtil.isLogin()) {
            try {
                Long uid = StpUtil.getLoginIdAsLong();
                User user = userService.getByUid(uid);
                if (user == null) {
                    // token 有效但用户已不存在，强制登出
                    log.warn("登录态有效但用户不存在 uid={} path={}", uid, path);
                    StpUtil.logout();
                    writeUnauthorized(response, path);
                    return false;
                }
                UserContext.setUserId(user.getId());
            } catch (Exception e) {
                log.warn("解析当前登录用户失败 path={} msg={}", path, e.getMessage());
                writeUnauthorized(response, path);
                return false;
            }
            return true;
        }

        writeUnauthorized(response, path);
        return false;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 无论成功失败都必须清除，避免 Tomcat 线程复用时 ThreadLocal 残留造成内存泄漏
        UserContext.clear();
    }

    private boolean isExclude(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        for (String p : EXCLUDE_PATHS) {
            if (path.equals(p) || path.startsWith(p + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写未登录 JSON 响应：HTTP 401 + BaseResult 统一格式
     */
    private void writeUnauthorized(HttpServletResponse response, String path) throws Exception {
        log.debug("未登录访问被拦截 path={}", path);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        BaseResult<Void> body = BaseResult.newAuthError();
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
