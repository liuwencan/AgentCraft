package cn.lwccxy.agentcraft.entity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 登录请求参数
 * <p>type=1：手机号 + 验证码（phone + code），手机号不存在则自动注册。</p>
 * <p>type=2：账号 + 密码（account 可为 uid 或手机号，password）。</p>
 */
@Data
public class LoginDTO {

    /**
     * 登录方式：1=手机号验证码，2=账号密码
     */
    @NotNull(message = "登录方式不能为空")
    private Integer type;

    /**
     * 手机号（type=1 使用）
     */
    private String phone;

    /**
     * 验证码（type=1 使用）
     */
    private String code;

    /**
     * 账号，uid 或手机号（type=2 使用）
     */
    private String account;

    /**
     * 密码（type=2 使用）
     */
    private String password;
}
