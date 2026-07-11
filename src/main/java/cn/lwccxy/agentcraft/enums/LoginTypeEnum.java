package cn.lwccxy.agentcraft.enums;

import java.util.Arrays;

/**
 * 登录方式枚举
 * <p>前端 type 参数：1=手机号+验证码，2=账号(uid/手机号)+密码</p>
 */
public enum LoginTypeEnum {

    /** 手机号 + 验证码（手机号不存在时自动注册） */
    PHONE_CODE(1, "手机号验证码登录"),

    /** uid / 手机号 + 密码 */
    PASSWORD(2, "账号密码登录");

    private final int code;
    private final String desc;

    LoginTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据 code 解析枚举，非法值返回 null
     */
    public static LoginTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.code == code)
                .findFirst()
                .orElse(null);
    }
}
