package cn.lwccxy.agentcraft.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI会话实体类
 * 对应数据库表 ai_session
 */
@Data
@TableName("ai_session")
public class AiSession {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话ID
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 智能体类型（react/file/ppt）
     */
    @TableField("agent_type")
    private String agentType;

    /**
     * 用户问题
     */
    @TableField("question")
    private String question;

    /**
     * AI回复
     */
    @TableField("answer")
    private String answer;

    /**
     * 涉及的执行工具名称（逗号分隔）
     */
    @TableField("tools")
    private String tools;

    /**
     * 参考链接JSON
     */
    @TableField("reference")
    private String reference;

    /**
     * 首次响应时间（毫秒）
     */
    @TableField("first_response_time")
    private Long firstResponseTime;

    /**
     * 整体回复时间（毫秒）
     */
    @TableField("total_response_time")
    private Long totalResponseTime;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 思考过程
     */
    @TableField("thinking")
    private String thinking;

    /**
     * 关联文件ID（用于关联ai_file_info或ai_ppt_inst）
     */
    @TableField("fileid")
    private String fileid;

    /**
     * 推荐问题JSON
     */
    @TableField("recommend")
    private String recommend;
}
