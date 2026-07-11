package cn.lwccxy.agentcraft.context;

/**
 * 当前线程用户上下文（存的是 users.id 自增主键，不是业务 uid）
 * <p>由 {@link cn.lwccxy.agentcraft.UserInterceptor} 在请求进入时设置，请求结束后清除。
 * 业务层可通过 {@link #currentUserId()} 在任何地方获取当前登录用户的数据库主键 ID。</p>
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 设置当前请求登录用户的 users.id 主键
     */
    public static void setUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    /**
     * 读取当前请求登录用户的 users.id；未登录返回 null
     */
    public static Long currentUserId() {
        return CURRENT_USER_ID.get();
    }

    /**
     * 清除当前线程绑定，防止线程复用导致内存泄漏
     */
    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
