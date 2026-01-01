package cn.lwccxy.agentcraft.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元数据实体类
 * 对应数据库表 ai_file_info
 */
@Data
@TableName("ai_file_info")
public class AiFileInfo {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 文件唯一标识
     */
    @TableField("file_id")
    private String fileId;

    /**
     * 原始文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 文件类型（pdf/doc/docx/txt/png/jpg等）
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * MinIO中的存储路径
     */
    @TableField("minio_path")
    private String minioPath;

    /**
     * 解析后的纯文本内容
     */
    @TableField("extracted_text")
    private String extractedText;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 会话ID（可选，用于关联特定会话）
     */
    @TableField("conversation_id")
    private String conversationId;

    /**
     * 文件状态
     */
    @TableField("status")
    private String status;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 是否已向量化（大文件标识）
     * 0-未向量化，1-已向量化
     */
    @TableField("embed")
    private Integer embed;
}
