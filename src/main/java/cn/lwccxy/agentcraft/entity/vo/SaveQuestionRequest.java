package cn.lwccxy.agentcraft.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保存用户问题请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveQuestionRequest {

    /**
     * 会话ID（必填）
     */
    private String sessionId;

    /**
     * 用户问题（必填）
     */
    private String question;

    /**
     * 关联文件ID（可选）
     */
    private String fileid;

    /**
     * 使用的工具名称（逗号分隔，可选）
     */
    private String tools;

    /**
     * 首次响应时间（毫秒，可选）
     */
    private Long firstResponseTime;
}
