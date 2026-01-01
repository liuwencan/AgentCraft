package cn.lwccxy.agentcraft.entity.record.pptx;

import lombok.Getter;

/**
 * PPT页面类型枚举
 */
@Getter
public enum PptPageType {

    /**
     * 封面页
     */
    COVER("COVER", "封面页"),
    /**
     * 目录页
     */
    CATALOG("CATALOG", "目录页"),
    /**
     * 内容页
     */
    CONTENT("CONTENT", "内容页"),
    /**
     * 对比页
     */
    COMPARE("COMPARE", "对比页"),
    /**
     * 结束页
     */
    END("END", "结束页");

    private final String code;
    private final String desc;

    PptPageType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     */
    public static PptPageType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (PptPageType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
