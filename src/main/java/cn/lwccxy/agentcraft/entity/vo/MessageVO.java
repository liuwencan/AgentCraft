package cn.lwccxy.agentcraft.entity.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息VO
 */
@Data
@Builder
public class MessageVO {
    /**
     * 记录ID
     */
    private Long id;

    /**
     * 用户问题
     */
    private String question;

    /**
     * AI回复
     */
    private String answer;

    /**
     * 思考过程
     */
    private String thinking;

    /**
     * 使用的工具
     */
    private String tools;

    /**
     * 参考链接
     */
    private String reference;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 文件ID（关联文件或PPT）
     */
    private String fileid;

    /**
     * 推荐问题
     */
    private String recommend;
}
