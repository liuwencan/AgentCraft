package cn.lwccxy.agentcraft.entity.vo;

import cn.lwccxy.agentcraft.entity.AiSession;
import lombok.Builder;
import lombok.Data;

/**
 * 会话列表VO
 */
@Data
@Builder
public class SessionListVO {
    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 智能体类型（react/file/ppt）
     */
    private String agentType;

    /**
     * 最新问题
     */
    private String question;

    /**
     * 最新回答
     */
    private String answer;

    /**
     * 消息数量
     */
    private Integer messageCount;

    /**
     * 创建时间
     */
    private java.time.LocalDateTime createTime;

    /**
     * 更新时间
     */
    private java.time.LocalDateTime updateTime;

    /**
     * 文件ID（关联文件或PPT）
     */
    private String fileid;

    /**
     * 从AiSession构建
     */
    public static SessionListVO fromAiSession(AiSession session, Integer messageCount) {
        return SessionListVO.builder()
                .conversationId(session.getSessionId())
                .agentType(session.getAgentType())
                .question(session.getQuestion())
                .answer(session.getAnswer())
                .messageCount(messageCount)
                .createTime(session.getCreateTime())
                .updateTime(session.getUpdateTime())
                .fileid(session.getFileid())
                .build();
    }
}
