package cn.lwccxy.agentcraft.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 用户状态枚举，对应 users.status（tinyint）
 * <p>MyBatis-Plus 通过 {@link EnumValue} 持久化 code；Jackson 通过 {@link JsonValue} 序列化 code。</p>
 */
public enum UserStatusEnum {

    /** 禁用 / 封禁 */
    DISABLED(0, "禁用"),

    /** 正常 */
    ENABLED(1, "正常");

    @EnumValue
    private final int code;
    private final String desc;

    UserStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
