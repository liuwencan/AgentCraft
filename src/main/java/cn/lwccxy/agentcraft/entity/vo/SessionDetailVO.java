package cn.lwccxy.agentcraft.entity.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 会话详情VO
 */
@Data
@Builder
public class SessionDetailVO {
    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 智能体类型（react/file/ppt）
     */
    private String agentType;

    /**
     * 消息列表
     */
    private List<MessageVO> messages;

    /**
     * 文件ID（关联文件或PPT）
     */
    private String fileid;
}
