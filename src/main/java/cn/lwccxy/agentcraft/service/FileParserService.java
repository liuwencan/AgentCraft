package cn.lwccxy.agentcraft.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import java.util.List;

/**
 * 文件解析服务
 * 负责解析不同类型文件的内容
 * - PDF: 使用 Apache PDFBox 解析
 * - DOC/DOCX: 使用 Apache POI 解析
 * - TXT: 直接读取
 * - 图片: 使用 Qwen-VL 进行 OCR 识别
 */
@Service
@Slf4j
public class FileParserService {

    /**
     * 最大文本内容长度限制
     */
    private static final int MAX_TEXT_LENGTH = 20000;

    /**
     * 解析上传的文件并返回文本内容
     *
     * @param file 上传的文件
     * @return 解析结果（包含全量文本和截断文本）
     */
    public ParseResult parseFile(MultipartFile file) {
        String fullText = parseFileInternal(file);
        String truncatedText = truncateIfNeeded(fullText);
        return new ParseResult(fullText, truncatedText);
    }

    /**
     * 文件解析结果
     */
    @Data
    @AllArgsConstructor
    public static class ParseResult {
        /**
         * 全量文本
         */
        private String fullText;

        /**
         * 截断文本
         */
        private String truncatedText;
    }

    /**
     * 内部方法：解析文件并返回完整文本内容
     *
     * @param file 上传的文件
     * @return 解析后的完整文本内容
     */
    private String parseFileInternal(MultipartFile file) {
        String fileType = getFileType(file.getOriginalFilename());
        long fileSize = file.getSize();

        log.info("开始解析文件: {} (类型: {}, 大小: {} bytes)", file.getOriginalFilename(), fileType, fileSize);

        try {
            String content;
            switch (fileType.toLowerCase()) {
                case "pdf":
                    content = parsePdf(file);
                    break;
                case "docx":
                    content = parseDocx(file);
                    break;
                case "doc":
                    throw new IllegalArgumentException("暂不支持 .doc 格式，请转换为 .docx");
                case "txt":
                    content = parseTxt(file);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的文件类型: " + fileType);
            }

            log.info("文件解析完成，内容长度: {} 字符", content.length());
            return content;
        } catch (Exception e) {
            log.error("文件解析失败: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 如果需要则截断文本
     *
     * @param content 原始文本内容
     * @return 截断后的文本内容
     */
    private String truncateIfNeeded(String content) {
        if (content.length() > MAX_TEXT_LENGTH) {
            log.warn("文件内容过长，将截断至 {} 字符", MAX_TEXT_LENGTH);
            return content.substring(0, MAX_TEXT_LENGTH) + "\n\n... (内容已截断，文件过长)";
        }
        return content;
    }

    /**
     * 解析 PDF 文件
     */
    private String parsePdf(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is)) {

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            String text = stripper.getText(document);
            log.info("PDF 解析完成，页数: {}, 文本长度: {}",
                    document.getNumberOfPages(), text.length());

            return text.trim();
        }
    }

    /**
     * 解析 DOCX 文件
     */
    private String parseDocx(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {

            StringBuilder text = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            for (XWPFParagraph paragraph : paragraphs) {
                String paraText = paragraph.getText();
                if (paraText != null && !paraText.trim().isEmpty()) {
                    text.append(paraText).append("\n");
                }
            }

            log.info("DOCX 解析完成，段落数: {}, 文本长度: {}",
                    paragraphs.size(), text.length());

            return text.toString().trim();
        }
    }

    /**
     * 解析 TXT 文件
     */
    private String parseTxt(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream()) {
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.info("TXT 解析完成，文本长度: {}", text.length());
            return text.trim();
        }
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
            return fileName.substring(lastDotIndex + 1);
        }
        return "unknown";
    }
}
