package cn.lwccxy.agentcraft.service.impl;

import cn.lwccxy.agentcraft.entity.User;
import cn.lwccxy.agentcraft.enums.UserRoleEnum;
import cn.lwccxy.agentcraft.enums.UserStatusEnum;
import cn.lwccxy.agentcraft.mapper.UserMapper;
import cn.lwccxy.agentcraft.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 用户服务实现类
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /** 自动注册默认密码 */
    private static final String DEFAULT_PASSWORD = "123456";
    /** 昵称随机字符池 */
    private static final char[] NICKNAME_POOL =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    /** uid 取值范围：9 位数字 [100000000, 999999999] */
    private static final long UID_MIN = 100_000_000L;
    private static final long UID_BOUND = 900_000_000L;
    /** uid 生成最大重试次数 */
    private static final int UID_MAX_RETRY = 10;

    private final SecureRandom random = new SecureRandom();
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User getByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone), false);
    }

    @Override
    public User getByUid(Long uid) {
        if (uid == null) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<User>().eq(User::getUid, uid), false);
    }

    @Override
    public User getByAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return null;
        }
        // 纯数字优先按 uid 查；11 位手机号或查不到时按手机号查
        if (account.matches("^\\d{1,18}$") && account.length() != 11) {
            return getByUid(Long.parseLong(account));
        }
        User byPhone = getByPhone(account);
        if (byPhone != null) {
            return byPhone;
        }
        // 兜底：11 位纯数字也可能是 uid
        if (account.matches("^\\d+$")) {
            try {
                return getByUid(Long.parseLong(account));
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    @Override
    public User registerByPhone(String phone) {
        User user = new User();
        user.setUid(generateUniqueUid());
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setNickname(generateNickname());
        user.setAvatar("");
        user.setLoginCount(0L);
        user.setStatus(UserStatusEnum.ENABLED);
        user.setRole(UserRoleEnum.USER);
        try {
            this.save(user);
        } catch (DuplicateKeyException e) {
            // 并发下同一手机号可能已被注册，回查已存在记录
            log.warn("手机号自动注册冲突，回查已有用户: phone={}", desensitizePhone(phone));
            User exist = getByPhone(phone);
            if (exist != null) {
                return exist;
            }
            throw e;
        }
        return user;
    }

    @Override
    public boolean matchesPassword(String rawPassword, User user) {
        if (user == null || !StringUtils.hasText(rawPassword) || !StringUtils.hasText(user.getPassword())) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    @Override
    public void recordLoginSuccess(User user, String ip, String device) {
        User update = new User();
        update.setId(user.getId());
        update.setLastLoginIp(ip);
        update.setLastLoginTime(LocalDateTime.now());
        update.setLastLoginDevice(device);
        update.setLoginCount((user.getLoginCount() == null ? 0L : user.getLoginCount()) + 1);
        this.updateById(update);
    }

    /**
     * 生成唯一 uid，冲突时重试
     */
    private Long generateUniqueUid() {
        for (int i = 0; i < UID_MAX_RETRY; i++) {
            long uid = UID_MIN + (long) (random.nextDouble() * UID_BOUND);
            if (getByUid(uid) == null) {
                return uid;
            }
        }
        throw new IllegalStateException("生成用户ID失败，请重试");
    }

    /**
     * 生成昵称：Agent_ + 6 位随机 [a-zA-Z0-9]
     */
    private String generateNickname() {
        StringBuilder sb = new StringBuilder("Agent_");
        for (int i = 0; i < 6; i++) {
            sb.append(NICKNAME_POOL[random.nextInt(NICKNAME_POOL.length)]);
        }
        return sb.toString();
    }

    private String desensitizePhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
