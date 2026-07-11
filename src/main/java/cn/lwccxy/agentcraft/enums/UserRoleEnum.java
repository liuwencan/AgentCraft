package cn.lwccxy.agentcraft.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 用户角色枚举，对应 users.role（varchar）
 */
public enum UserRoleEnum {

    /** 普通用户 */
    USER("USER", "普通用户"),

    /** 管理员 */
    ADMIN("ADMIN", "管理员");

    @EnumValue
    private final String code;
    private final String desc;

    UserRoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
