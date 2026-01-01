package cn.lwccxy.agentcraft.entity.record.pptx;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI PPT 实例实体类
 * 对应数据库表 ai_ppt_inst
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_ppt_inst")
public class AiPptInst {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话ID
     */
    @TableField("conversation_id")
    private String conversationId;

    /**
     * 模板编码
     */
    @TableField("template_code")
    private String templateCode;

    /**
     * 状态
     */
    @TableField("status")
    private String status;

    /**
     * 用户原始需求
     */
    @TableField("query")
    private String query;

    /**
     * 需求澄清结果
     */
    @TableField("requirement")
    private String requirement;

    /**
     * 搜索结果
     */
    @TableField("search_info")
    private String searchInfo;

    /**
     * PPT大纲
     */
    @TableField("outline")
    private String outline;

    /**
     * 最终渲染JSON
     */
    @TableField("ppt_schema")
    private String pptSchema;

    /**
     * 生成PPT文件URL
     */
    @TableField("file_url")
    private String fileUrl;

    /**
     * 失败原因
     */
    @TableField("error_msg")
    private String errorMsg;

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
     * 获取状态枚举
     */
    public PptInstStatus getStatusEnum() {
        return PptInstStatus.fromCode(status);
    }

    /**
     * 设置状态枚举
     */
    public void setStatusEnum(PptInstStatus statusEnum) {
        this.status = statusEnum != null ? statusEnum.getCode() : null;
    }
}
