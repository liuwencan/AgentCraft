package cn.lwccxy.agentcraft.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新AI回复请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAnswerRequest {

    /**
     * 记录ID（必填）
     */
    private Long id;

    /**
     * AI回复（必填）
     */
    private String answer;

    /**
     * 思考过程（可选）
     */
    private String thinking;

    /**
     * 使用的工具名称（逗号分隔，可选）
     */
    private String tools;

    /**
     * 参考链接JSON（可选）
     */
    private String reference;

    /**
     * 首次响应时间（毫秒，可选）
     */
    private Long firstResponseTime;

    /**
     * 总响应时间（毫秒，可选）
     */
    private Long totalResponseTime;

    /**
     * 推荐问题JSON（可选）
     */
    private String recommend;
}
