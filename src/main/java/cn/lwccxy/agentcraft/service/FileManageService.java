package cn.lwccxy.agentcraft.service;

import cn.lwccxy.agentcraft.entity.record.FileInfo;
import cn.lwccxy.agentcraft.service.impl.FileInfoServiceImpl;
import cn.lwccxy.agentcraft.splitter.OverlapParagraphTextSplitter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 文件管理服务
 * 提供文件上传、查询、删除等纯业务功能
 */
@Service
@Slf4j
public class FileManageService {

    @Autowired
    private MinioService minioService;

    @Autowired
    private FileParserService fileParserService;

    @Autowired
    private FileInfoServiceImpl fileInfoService;

    @Autowired
    private EmbeddingService embeddingService;

    private OpenAiChatModel multimodalChatModel;

    /**
     * 大文件阈值（字符数）
     */
    private static final int LARGE_FILE_THRESHOLD = 5000;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    /**
     * 初始化多模态模型（用于图片识别）
     */
    @PostConstruct
    public void init() {
        try {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .temperature(0.2d)
                    .model("qwen3-vl-plus")
                    .build();
            multimodalChatModel = OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder()
                            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/")
                            .apiKey(new SimpleApiKey(apiKey))
                            .build())
                    .defaultOptions(options)
                    .build();
            log.info("多模态模型初始化成功");
        } catch (Exception e) {
            log.warn("多模态模型初始化失败: {}", e.getMessage());
        }
    }

    /**
     * 上传文件
     *
     * @param file 上传的文件
     * @return 文件信息
     */
    @Transactional(rollbackFor = Exception.class)
    public FileInfo uploadFile(MultipartFile file) {
        String fileId = UUID.randomUUID().toString();
        String fileType = getFileType(file.getOriginalFilename());
        long fileSize = file.getSize();

        log.info("开始处理文件上传: fileId={}, fileName={}, fileType={}, fileSize={}", fileId, file.getOriginalFilename(), fileType, fileSize);

        try {
            // 创建文件信息
            FileInfo fileInfo = FileInfo.builder()
                    .fileId(fileId)
                    .fileName(file.getOriginalFilename())
                    .fileType(fileType)
                    .fileSize(fileSize)
                    .createdAt(LocalDateTime.now())
                    .status(FileInfo.FileStatus.PROCESSING)
                    .build();

            // 先保存到数据库（初始状态为PROCESSING）
            fileInfoService.saveFileInfo(fileInfo);

            // 上传到 MinIO
            String objectName = generateObjectName(fileId, fileType);
            String minioPath = minioService.uploadFile(file, objectName);
            log.info("MinIO 上传完成: fileId={}", fileId);

            // 更新MinIO路径
            fileInfo.setMinioPath(minioPath);
            fileInfo.setStatus(FileInfo.FileStatus.SUCCESS);
            fileInfoService.updateFileInfo(fileInfo);

            // 根据文件类型进行不同的处理
            if (isTextFile(fileType)) {
                try {
                    var parseResult = fileParserService.parseFile(file);
                    String fullText = parseResult.getFullText();
                    String extractedText = parseResult.getTruncatedText();

                    log.info("文件解析完成: fileId={}, 全量文本长度: {}, 截断后长度: {}",
                            fileId, fullText.length(), extractedText.length());

                    // 存储截断后的文本用于展示
                    fileInfo.setExtractedText(extractedText);
                    fileInfoService.updateFileInfo(fileInfo);

                    // 判断是否为大文件，如果是则使用全量文本进行向量化
                    if (isLargeFile(fullText)) {
                        log.info("检测到大文件，开始向量化处理: fileId={}, 全量文本长度: {}", fileId, fullText.length());
                        try {
                            processLargeFileEmbedding(fileId, fullText);
                            fileInfo.setEmbed(1);
                            fileInfoService.updateFileInfo(fileInfo);
                            log.info("大文件向量化完成: fileId={}", fileId);
                        } catch (Exception e) {
                            log.error("大文件向量化失败: fileId={}", fileId, e);
                            // 向量化失败不影响文件上传，embed 保持为 0
                        }
                    }
                } catch (Exception e) {
                    log.error("文件解析失败: fileId={}", fileId, e);
                    fileInfo.setStatus(FileInfo.FileStatus.FAILED);
                    fileInfoService.updateFileInfo(fileInfo);
                    throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
                }
            } else if (isImageFile(fileType)) {
                // 图片文件：调用多模态AI识别图片内容
                try {
                    String extractedText = image2Text(file);
                    fileInfo.setExtractedText(extractedText);
                    fileInfoService.updateFileInfo(fileInfo);
                    log.info("图片识别完成: fileId={}, 识别文本长度: {}", fileId, extractedText.length());
                } catch (Exception e) {
                    log.error("图片识别失败: fileId={}", fileId, e);
                    fileInfo.setStatus(FileInfo.FileStatus.FAILED);
                    fileInfoService.updateFileInfo(fileInfo);
                    throw new RuntimeException("图片识别失败: " + e.getMessage(), e);
                }
            } else {
                // 其他文件类型：标记为成功，不进行额外处理
                log.info("其他类型文件上传完成: fileId={}, 类型: {}", fileId, fileType);
            }

            log.info("文件上传完成: fileId={}", fileId);

            return fileInfo;
        } catch (Exception e) {
            log.error("文件上传失败: fileId={}", fileId, e);

            // 更新数据库中的状态为失败
            FileInfo fileInfo = fileInfoService.getFileInfoById(fileId);
            if (fileInfo != null) {
                fileInfo.setStatus(FileInfo.FileStatus.FAILED);
                fileInfoService.updateFileInfo(fileInfo);
            }

            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 识别图片内容
     *
     * @param file 图片文件
     * @return 图片内容的详细描述
     */
    private String image2Text(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] imageBytes = IOUtils.toByteArray(inputStream);

            if (imageBytes == null || imageBytes.length == 0) {
                throw new RuntimeException("图片文件内容为空");
            }

            // 使用多模态模型识别图片
            ByteArrayResource imageResource = new ByteArrayResource(imageBytes);
            var userMessage = UserMessage.builder()
                    .text("请描述这张图片的内容，包括场景、对象、布局、颜色、文字信息，直接输出纯文本描述，不要多余说明，不要增加任何特殊符号，特别是换行符")
                    .media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageResource)))
                    .build();
            var response = multimodalChatModel.call(new Prompt(List.of(userMessage)));
            String resp = response.getResult().getOutput().getText();

            if (resp == null || resp.trim().isEmpty()) {
                return "[无法识别图片内容]";
            }
            return resp.trim();
        } catch (Exception e) {
            log.error("图片识别异常", e);
            throw new RuntimeException("图片识别失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据 fileId 获取文件信息
     *
     * @param fileId 文件ID
     * @return 文件信息
     */
    public FileInfo getFileInfo(String fileId) {
        FileInfo fileInfo = fileInfoService.getFileInfoById(fileId);
        if (fileInfo == null) {
            throw new IllegalArgumentException("文件不存在: " + fileId);
        }
        return fileInfo;
    }

    /**
     * 获取文件处理状态（用于查询处理进度）
     *
     * @param fileId 文件ID
     * @return 处理状态描述
     */
    public String getFileProcessingStatus(String fileId) {
        FileInfo fileInfo = getFileInfo(fileId);

        switch (fileInfo.getStatus()) {
            case PROCESSING:
                return "文件正在处理中...";
            case SUCCESS:
                return "文件处理完成，可以查看内容";
            case FAILED:
                return "文件处理失败，请重试";
            default:
                return "未知状态";
        }
    }

    /**
     * 根据 fileId 获取文件内容
     *
     * @param fileId 文件ID
     * @return 文件内容
     */
    public String getFileContent(String fileId) {
        FileInfo fileInfo = getFileInfo(fileId);

        if (fileInfo.getStatus() != FileInfo.FileStatus.SUCCESS) {
            throw new IllegalStateException("文件尚未处理完成，当前状态: " + fileInfo.getStatus());
        }

        String content = fileInfo.getExtractedText();
        if (content == null || content.trim().isEmpty()) {
            return "该文件没有可识别的内容";
        }

        return content;
    }

    /**
     * 检查文件是否存在
     *
     * @param fileId 文件ID
     * @return 是否存在
     */
    public boolean exists(String fileId) {
        return fileInfoService.exists(fileId);
    }

    /**
     * 删除文件
     *
     * @param fileId 文件ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(String fileId) {
        FileInfo fileInfo = fileInfoService.getFileInfoById(fileId);
        if (fileInfo == null) {
            throw new IllegalArgumentException("文件不存在: " + fileId);
        }

        try {
            // 从 MinIO 删除
            if (fileInfo.getMinioPath() != null) {
                String objectName = extractObjectName(fileInfo.getMinioPath());
                minioService.deleteFile(objectName);
            }

            // 从数据库删除
            fileInfoService.deleteFileInfo(fileId);

            log.info("文件删除成功: fileId={}", fileId);
        } catch (Exception e) {
            log.error("文件删除失败: fileId={}", fileId, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取所有文件列表
     *
     * @return 文件列表
     */
    public Map<String, FileInfo> getAllFiles() {
        List<FileInfo> fileInfos = fileInfoService.getAllFiles();
        return fileInfos.stream()
                .collect(Collectors.toMap(FileInfo::getFileId, fileInfo -> fileInfo, (existing, replacement) -> existing, ConcurrentHashMap::new));
    }

    /**
     * 获取文件数量
     *
     * @return 文件数量
     */
    public int getFileCount() {
        return fileInfoService.getFileCount();
    }

    /**
     * 清理所有文件（用于测试）
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearAll() {
        // 获取所有文件ID，然后逐个删除
        List<FileInfo> allFiles = fileInfoService.getAllFiles();
        for (FileInfo fileInfo : allFiles) {
            try {
                deleteFile(fileInfo.getFileId());
            } catch (Exception e) {
                log.error("清理文件失败: fileId={}", fileInfo.getFileId(), e);
            }
        }
        log.info("所有文件已清理");
    }

    /**
     * 从文件名中提取文件类型
     */
    private String getFileType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unknown";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "unknown";
    }

    /**
     * 生成 MinIO 对象名称
     */
    public static String generateObjectName(String fileId, String fileType) {
        return "file-" + fileId.replace("-", "") + "." + fileType.toLowerCase();
    }

    /**
     * 判断是否为文本文件
     */
    private boolean isTextFile(String fileType) {
        return ("pdf".equalsIgnoreCase(fileType) ||
                "docx".equalsIgnoreCase(fileType) ||
                "doc".equalsIgnoreCase(fileType) ||
                "txt".equalsIgnoreCase(fileType));
    }

    /**
     * 判断是否为图片文件
     */
    private boolean isImageFile(String fileType) {
        return ("jpg".equalsIgnoreCase(fileType) ||
                "jpeg".equalsIgnoreCase(fileType) ||
                "png".equalsIgnoreCase(fileType) ||
                "gif".equalsIgnoreCase(fileType) ||
                "bmp".equalsIgnoreCase(fileType));
    }

    /**
     * 从完整路径中提取对象名称
     */
    private String extractObjectName(String fullPath) {
        if (fullPath == null || !fullPath.contains("/")) {
            return fullPath;
        }
        return fullPath.substring(fullPath.lastIndexOf("/") + 1);
    }

    /**
     * 判断是否为大文件
     *
     * @param text 文本内容
     * @return 是否为大文件
     */
    private boolean isLargeFile(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        return text.length() >= LARGE_FILE_THRESHOLD;
    }

    /**
     * 处理大文件向量化
     *
     * @param fileId 文件ID
     * @param text   文本内容
     */
    private void processLargeFileEmbedding(String fileId, String text) {
        log.info("开始处理大文件向量化: fileId={}, 文本长度: {}", fileId, text.length());

        // 1. 创建文档
        Document document = new Document(text);
        List<Document> documents = List.of(document);

        // 2. 切分文档（使用500字符，50重叠）
        OverlapParagraphTextSplitter splitter = new OverlapParagraphTextSplitter(500, 50);
        List<Document> chunks = splitter.apply(documents);
        log.info("文档切分完成: fileId={}, 切分数量: {}", fileId, chunks.size());

        // 3. 为每个切分添加元数据
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            chunk.getMetadata().put("fileid", fileId);
            chunk.getMetadata().put("chunkId", i);
        }

        // 4. 向量化并存储
        embeddingService.embedAndStore(chunks);
        log.info("大文件向量化存储完成: fileId={}, 切分数量: {}", fileId, chunks.size());
    }
}
