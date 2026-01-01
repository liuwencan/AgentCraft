package cn.lwccxy.agentcraft.entity.record;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件元数据模型
 * 存储文件的基本信息和解析后的内容
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {

    /**
     * 文件唯一标识
     */
    private String fileId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件类型（pdf/doc/docx/txt/png/jpg等）
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MinIO中的存储路径
     */
    private String minioPath;

    /**
     * 解析后的纯文本内容
     */
    private String extractedText;

    /**
     * 文件上传时间
     */
    private LocalDateTime createdAt;

    /**
     * 会话ID（可选，用于关联特定会话）
     */
    private String conversationId;

    /**
     * 文件状态
     */
    @Builder.Default
    private FileStatus status = FileStatus.PENDING;

    /**
     * 是否已向量化（大文件标识）
     * 0-未向量化，1-已向量化
     */
    @Builder.Default
    private Integer embed = 0;

    /**
     * 文件状态枚举
     */
    public enum FileStatus {
        /**
         * 待处理
         */
        PENDING,
        /**
         * 处理中
         */
        PROCESSING,
        /**
         * 处理成功
         */
        SUCCESS,
        /**
         * 处理失败
         */
        FAILED
    }

    /**
     * 判断文件是否已处理完成
     */
    public boolean isProcessed() {
        return status == FileStatus.SUCCESS && extractedText != null;
    }

    /**
     * 判断文件是否为图片
     */
    public boolean isImage() {
        return ("png".equalsIgnoreCase(fileType)
                || "jpg".equalsIgnoreCase(fileType)
                || "jpeg".equalsIgnoreCase(fileType)
                || "gif".equalsIgnoreCase(fileType)
                || "bmp".equalsIgnoreCase(fileType));
    }

    /**
     * 判断文件是否为PDF
     */
    public boolean isPdf() {
        return "pdf".equalsIgnoreCase(fileType);
    }

    /**
     * 判断文件是否为Word文档
     */
    public boolean isWord() {
        return ("doc".equalsIgnoreCase(fileType)
                || "docx".equalsIgnoreCase(fileType));
    }
}
