package cn.lwccxy.agentcraft.service;

import cn.lwccxy.agentcraft.sms.AliyunSmsHelper;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码服务（基于 Redisson）
 * <p>包含：发送频率限制（冷却 + 每日上限）、验证码存储与一次性校验、校验次数限制。</p>
 * <p>注意：此处未接入真实短信网关，验证码通过日志输出，联调使用。生产环境请替换 {@link #dispatch}。</p>
 */
@Service
public class VerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerificationCodeService.class);

    /** 验证码有效期 5 分钟 */
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    /** 同一手机号发送冷却 60 秒 */
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    /** 同一手机号每日发送上限 */
    private static final long DAILY_SEND_LIMIT = 10;
    /** 单个验证码最大校验次数 */
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private static final String KEY_CODE = "auth:code:%s";
    private static final String KEY_COOLDOWN = "auth:code:cd:%s";
    private static final String KEY_DAILY = "auth:code:daily:%s";
    private static final String KEY_ATTEMPT = "auth:code:try:%s";

    private final SecureRandom random = new SecureRandom();
    private final RedissonClient redisson;

    public VerificationCodeService(RedissonClient redisson) {
        this.redisson = redisson;
    }

    /**
     * 发送验证码。触发频率限制时抛出 {@link IllegalArgumentException}（由全局异常处理器返回 400）。
     */
    public void sendCode(String phone) {
        String cdKey = String.format(KEY_COOLDOWN, phone);
        // 冷却期判断
        if (redisson.getBucket(cdKey).isExists()) {
            throw new IllegalArgumentException("验证码发送过于频繁，请稍后再试");
        }
        // 每日上限自增
        String dailyKey = String.format(KEY_DAILY, phone);
        long count = redisson.getAtomicLong(dailyKey).incrementAndGet();
        if (count == 1L) {
            redisson.getKeys().expire(dailyKey, 1, TimeUnit.DAYS);
        }
        if (count > DAILY_SEND_LIMIT) {
            throw new IllegalArgumentException("今日验证码发送次数已达上限");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        RBucket<String> codeBucket = redisson.getBucket(String.format(KEY_CODE, phone));
        codeBucket.set(code, CODE_TTL);
        redisson.getBucket(cdKey).set("1", SEND_COOLDOWN);
        // 重置该手机号的校验次数
        redisson.getKeys().delete(String.format(KEY_ATTEMPT, phone));

        dispatch(phone, code);
    }

    /**
     * 校验验证码。成功后立即失效（一次性）。超过最大尝试次数直接失效并拒绝。
     *
     * @return true=校验通过
     */
    public boolean verifyCode(String phone, String inputCode) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(inputCode)) {
            return false;
        }
        String codeKey = String.format(KEY_CODE, phone);
        RBucket<String> bucket = redisson.getBucket(codeKey);
        String saved = bucket.get();
        if (saved == null) {
            return false;
        }

        String attemptKey = String.format(KEY_ATTEMPT, phone);
        long attempts = redisson.getAtomicLong(attemptKey).incrementAndGet();
        if (attempts == 1L) {
            redisson.getKeys().expire(attemptKey, 5, TimeUnit.MINUTES);
        }
        if (attempts > MAX_VERIFY_ATTEMPTS) {
            // 尝试过多，作废当前验证码
            redisson.getKeys().delete(codeKey);
            redisson.getKeys().delete(attemptKey);
            throw new IllegalArgumentException("验证码错误次数过多，请重新获取");
        }

        boolean matched = saved.equals(inputCode.trim());
        if (matched) {
            redisson.getKeys().delete(codeKey);
            redisson.getKeys().delete(attemptKey);
        }
        return matched;
    }

    @Autowired
    private AliyunSmsHelper aliyunSmsHelper;

    /**
     * 实际下发验证码。
     */
    private void dispatch(String phone, String code) {

        // 调用第三方短信发送服务
//        String signName = "速通互联验证码";
//        String templateCode = "100001";
//        String templateParam = String.format("{\"code\":\"%s\",\"min\":\"%s\"}", code , 5);
//        aliyunSmsHelper.sendMessage(signName, templateCode, phone, templateParam);

        log.info("==> 手机号: {}, 已发送验证码：【{}】（有效期{}分钟）", phone, code, CODE_TTL.toMinutes());
    }

    private String mask(String phone) {
        if (phone == null || phone.length() != 11) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
