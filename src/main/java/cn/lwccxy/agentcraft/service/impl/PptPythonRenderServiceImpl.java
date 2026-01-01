package cn.lwccxy.agentcraft.service.impl;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.record.pptx.AiPptTemplate;
import cn.lwccxy.agentcraft.service.AiPptTemplateService;
import cn.lwccxy.agentcraft.service.PptPythonRenderService;
import cn.lwccxy.agentcraft.service.MinioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * PPT Python 渲染服务实现
 */
@Slf4j
@Service
public class PptPythonRenderServiceImpl implements PptPythonRenderService {

    private final AiPptTemplateService templateService;
    private final MinioService minioService;

    public PptPythonRenderServiceImpl(AiPptTemplateService templateService, MinioService minioService) {
        this.templateService = templateService;
        this.minioService = minioService;
    }

    @Override
    public String renderPpt(AiPptInst inst, String pptSchema) throws Exception {

        log.info("开始渲染PPT: instId={}", inst.getId());

        // ---------- 获取模板 ----------
        AiPptTemplate template = templateService.getByCode(inst.getTemplateCode());
        if (template == null) {
            throw new RuntimeException("模板不存在: " + inst.getTemplateCode());
        }

        String pythonScriptPath = getPythonScriptPath();
        String templateFilePath = template.getFilePath();
        String outputDir = getOutputDir();

        String outputFileName = "ppt_" + inst.getId() + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".pptx";

        String outputFilePath = outputDir + File.separator + outputFileName;

        File templateFile = new File(templateFilePath);
        if (!templateFile.exists()) {
            throw new RuntimeException("模板文件不存在: " + templateFilePath);
        }

        // ---------- 构建命令 ----------
        List<String> command = List.of(
                "python",
                pythonScriptPath,
                "--template", templateFilePath,
                "--output", outputFilePath
        );

        log.info("执行Python命令: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Map<String, String> env = pb.environment();

        env.put("PYTHONIOENCODING", "utf-8");

        // ---------- 处理 JSON 传递 ----------
        // Windows 环境变量长度有限（32KB），大 JSON 会失败
        // 超过 20KB 自动写入临时文件
        if (pptSchema.length() > 20000) {

            Path tempFile = Files.createTempFile("ppt_schema_", ".json");
            Files.writeString(tempFile, pptSchema, StandardOpenOption.TRUNCATE_EXISTING);

            env.put("PPT_SCHEMA_FILE", tempFile.toAbsolutePath().toString());
            log.info("JSON过大，使用临时文件传递: {}", tempFile);

        } else {
            env.put("PPT_SCHEMA", pptSchema);
        }

        // ---------- 启动 ----------
        Process process = pb.start();

        // ---------- 读取输出 ----------
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.info("Python输出: {}", line);
            }
        }

        // ---------- 等待（最多5分钟） ----------
        long timeoutMs = 5 * 60 * 1000L;
        long startTime = System.currentTimeMillis();
        boolean finished = false;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                int exitCode = process.exitValue();
                // 如果能获取到退出码，说明进程已结束
                finished = true;
                if (exitCode != 0) {
                    log.error("Python执行失败: {}", output);
                    throw new RuntimeException("Python脚本执行失败:\n" + output);
                }
                break;
            } catch (IllegalThreadStateException e) {
                // 进程还在运行，继续等待
                Thread.sleep(1000);
            }
        }

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Python执行超时");
        }

        int exitCode = process.exitValue();

        if (exitCode != 0) {
            log.error("Python执行失败: {}", output);
            throw new RuntimeException("Python脚本执行失败:\n" + output);
        }

        // ---------- 检查输出 ----------
        File outputFile = new File(outputFilePath);
        if (!outputFile.exists()) {
            throw new RuntimeException("PPT未生成: " + outputFilePath);
        }

        // ---------- 上传到MinIO ----------
        log.info("PPT生成成功，开始上传到MinIO");
        byte[] fileBytes = Files.readAllBytes(outputFile.toPath());

        // 构建MinIO对象名称: ppt/{conversationId}/{filename}
        String objectName = "ppt/" + inst.getConversationId() + "/" + outputFileName;

        String fileUrl = minioService.uploadFile(objectName, fileBytes, "application/vnd.openxmlformats-officedocument.presentationml.presentation");

        log.info("PPT已上传到MinIO: {}", fileUrl);

        // ---------- 删除本地文件 ----------
        try {
            Files.deleteIfExists(outputFile.toPath());
            log.info("本地PPT文件已删除: {}", outputFilePath);
        } catch (Exception e) {
            log.warn("删除本地文件失败: {}", outputFilePath, e);
        }

        return fileUrl;
    }
    /**
     * 获取Python脚本路径
     */
    private String getPythonScriptPath() {
        // 优先从环境变量读取，方便不同环境部署
        String envPath = System.getenv("AGENTCRAFT_PYTHON_SCRIPT");
        if (envPath != null && !envPath.isEmpty()) {
            return envPath;
        }
        // 默认路径（请根据实际部署修改，或通过环境变量 AGENTCRAFT_PYTHON_SCRIPT 覆盖）
        return "D:\\code\\java-project\\AgentCraft\\src\\main\\resources\\python\\render_ppt.py";
    }

    /**
     * 获取输出目录（用于临时存储）
     */
    private String getOutputDir() {
        String projectRoot = System.getProperty("user.dir");
        String outputDir = projectRoot + File.separator + "output" + File.separator + "ppt";
        try {
            Path path = Paths.get(outputDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            log.error("创建输出目录失败: {}", outputDir, e);
        }
        return outputDir;
    }
}
