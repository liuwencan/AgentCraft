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
 * AI PPT 模板实体类
 * 对应数据库表 ai_ppt_template
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_ppt_template")
public class AiPptTemplate {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 模板唯一编码
     */
    @TableField("template_code")
    private String templateCode;

    /**
     * 模板名称
     */
    @TableField("template_name")
    private String templateName;

    /**
     * 模板说明（给AI理解）
     */
    @TableField("template_desc")
    private String templateDesc;

    /**
     * 模板结构JSON（页面+占位符）
     */
    @TableField("template_schema")
    private String templateSchema;

    /**
     * PPT模板文件路径
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 风格标签：科技,商务,简约
     */
    @TableField("style_tags")
    private String styleTags;

    /**
     * 模板页数
     */
    @TableField("slide_count")
    private Integer slideCount;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
