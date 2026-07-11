package cn.lwccxy.agentcraft.entity.vo;

import lombok.Data;

/**
 * 登录成功返回：token + 用户信息
 * <p>Sa-Token 为 Cookie 模式，token 会自动写入 Cookie；同时在响应体返回一份，
 * 方便前端存入 localStorage 并在 Header 携带。</p>
 */
@Data
public class LoginVO {

    /** Sa-Token 令牌值 */
    private String token;

    /** 当前登录用户信息 */
    private UserInfoVO user;

    public LoginVO() {
    }

    public LoginVO(String token, UserInfoVO user) {
        this.token = token;
        this.user = user;
    }
}
