package cn.lwccxy.agentcraft.entity.record.pptx;

import lombok.Getter;

/**
 * PPT意图枚举
 */
@Getter
public enum PptIntent {

    /**
     * 新建PPT
     */
    CREATE_PPT("CREATE_PPT", "新建PPT"),
    /**
     * 修改PPT
     */
    MODIFY_PPT("MODIFY_PPT", "修改PPT"),
    /**
     * 断点重连（继续之前失败的任务）
     */
    RESUME_PPT("RESUME_PPT", "断点重连（继续之前失败的任务）");

    private final String code;
    private final String desc;

    PptIntent(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     */
    public static PptIntent fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (PptIntent intent : values()) {
            if (intent.code.equals(code)) {
                return intent;
            }
        }
        return null;
    }
}
