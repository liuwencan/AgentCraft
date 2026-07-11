package cn.lwccxy.agentcraft.service;

import cn.lwccxy.agentcraft.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 根据手机号查询用户（未删除）
     */
    User getByPhone(String phone);

    /**
     * 根据业务 uid 查询用户（未删除）
     */
    User getByUid(Long uid);

    /**
     * 根据账号查询：账号为纯数字时按 uid 查，否则按手机号查
     */
    User getByAccount(String account);

    /**
     * 手机号自动注册：默认密码 123456（BCrypt），昵称 Agent_+6位随机字符
     *
     * @param phone 已通过验证码校验的手机号
     * @return 新注册用户
     */
    User registerByPhone(String phone);

    /**
     * 校验原始密码与用户加密密码是否匹配
     */
    boolean matchesPassword(String rawPassword, User user);

    /**
     * 记录一次成功登录（更新 IP / 时间 / 次数 / 设备）
     */
    void recordLoginSuccess(User user, String ip, String device);
}
