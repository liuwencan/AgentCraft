package cn.lwccxy.agentcraft.service;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 登录防护服务：基于 Redisson 的失败次数统计与锁定，抵御密码/验证码暴力破解。
 * <p>以「账号标识 + 客户端IP」为维度，连续失败达到阈值后锁定一段时间。</p>
 */
@Service
public class LoginGuardService {

    /** 锁定阈值：连续失败次数 */
    private static final long MAX_FAIL = 5;
    /** 失败计数窗口 / 锁定时长 */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);

    private static final String KEY_FAIL = "auth:login:fail:%s";

    private final RedissonClient redisson;

    public LoginGuardService(RedissonClient redisson) {
        this.redisson = redisson;
    }

    /**
     * 登录前校验是否已被锁定，若锁定抛出 {@link IllegalArgumentException}
     */
    public void assertNotLocked(String identifier, String ip) {
        String key = buildKey(identifier, ip);
        long count = redisson.getAtomicLong(key).get();
        if (count >= MAX_FAIL) {
            throw new IllegalArgumentException("账号登录失败次数过多，请 " + LOCK_DURATION.toMinutes() + " 分钟后再试");
        }
    }

    /**
     * 记录一次登录失败，返回累计失败次数
     */
    public long recordFailure(String identifier, String ip) {
        String key = buildKey(identifier, ip);
        RAtomicLong counter = redisson.getAtomicLong(key);
        long count = counter.incrementAndGet();
        if (count == 1L) {
            redisson.getKeys().expire(key, 5, TimeUnit.MINUTES);
        }
        return count;
    }

    /**
     * 登录成功后清除失败计数
     */
    public void clearFailure(String identifier, String ip) {
        redisson.getKeys().delete(buildKey(identifier, ip));
    }

    private String buildKey(String identifier, String ip) {
        String id = StringUtils.hasText(identifier) ? identifier : "-";
        String addr = StringUtils.hasText(ip) ? ip : "-";
        return String.format(KEY_FAIL, id + ":" + addr);
    }
}
