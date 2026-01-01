package cn.lwccxy.agentcraft.controller;

import cn.lwccxy.agentcraft.common.BaseResult;
import cn.lwccxy.agentcraft.entity.record.FileInfo;
import cn.lwccxy.agentcraft.service.FileManageService;
import com.alibaba.fastjson2.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件控制器
 * 提供文件上传、查询等接口
 */
@RestController
@RequestMapping("/file")
@Tag(name = "文件管理", description = "文件上传、查询等接口")
@Slf4j
public class FileController {

    @Autowired
    private FileManageService fileManageService;

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "上传文件并返回文件ID，支持PDF、DOC、DOCX、TXT、PNG、JPG等格式")
    public BaseResult uploadFile(@RequestParam("file") MultipartFile file) {

        log.info("收到文件上传请求: fileName={}, size={}", file.getOriginalFilename(), file.getSize());

        try {
            if (file.isEmpty()) {
                return BaseResult.newError("文件不能为空");
            }

            // 上传并处理文件
            FileInfo fileInfo = fileManageService.uploadFile(file);
            log.info("文件上传成功: fileId={}", fileInfo.getFileId());
            return BaseResult.newSuccess(fileInfo);

        } catch (Exception e) {
            log.error("文件上传失败", e);
            return BaseResult.newError("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件信息
     */
    @GetMapping("/info/{fileId}")
    @Operation(summary = "获取文件信息", description = "根据文件ID获取文件的基本信息")
    public BaseResult<FileInfo> getFileInfo(@PathVariable String fileId) {
        log.info("获取文件信息: fileId={}", fileId);

        try {
            FileInfo fileInfo = fileManageService.getFileInfo(fileId);

            return BaseResult.newSuccess(fileInfo);

        } catch (Exception e) {
            log.error("获取文件信息失败: fileId={}", fileId, e);
            return BaseResult.newError("获取文件信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件内容
     */
    @GetMapping("/content/{fileId}")
    @Operation(summary = "获取文件内容", description = "根据文件ID获取文件的文本内容")
    public BaseResult<Map<String, Object>> getFileContent(@PathVariable String fileId) {
        log.info("获取文件内容: fileId={}", fileId);

        try {
            String content = fileManageService.getFileContent(fileId);

            Map<String, Object> response = new HashMap<>();
            response.put("content", content);
            response.put("length", content.length());

            return BaseResult.newSuccess(response);

        } catch (Exception e) {
            log.error("获取文件内容失败: fileId={}", fileId, e);
            return BaseResult.newError("获取文件内容失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    @Operation(summary = "删除文件", description = "根据文件ID删除文件及其内容")
    public BaseResult<String> deleteFile(@PathVariable String fileId) {
        log.info("删除文件: fileId={}", fileId);

        try {
            fileManageService.deleteFile(fileId);

            return BaseResult.newSuccess("文件删除成功");

        } catch (Exception e) {
            log.error("删除文件失败: fileId={}", fileId, e);
            return BaseResult.newError("删除文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有文件列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取所有文件列表", description = "获取当前存储的所有文件信息")
    public BaseResult<Map<String, Object>> listFiles() {
        log.info("获取所有文件列表");

        try {
            var files = fileManageService.getAllFiles();
            int count = fileManageService.getFileCount();

            Map<String, Object> response = new HashMap<>();
            response.put("count", count);
            response.put("files", files);

            return BaseResult.newSuccess(response);

        } catch (Exception e) {
            log.error("获取文件列表失败", e);
            return BaseResult.newError("获取文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 检查文件是否存在
     */
    @GetMapping("/exists/{fileId}")
    @Operation(summary = "检查文件是否存在", description = "检查指定文件ID的文件是否存在")
    public BaseResult<Boolean> fileExists(@PathVariable String fileId) {
        log.info("检查文件是否存在: fileId={}", fileId);

        try {
            boolean exists = fileManageService.exists(fileId);

            return BaseResult.newSuccess(exists);

        } catch (Exception e) {
            log.error("检查文件存在失败: fileId={}", fileId, e);
            return BaseResult.newError("检查文件存在失败: " + e.getMessage());
        }
    }
}
