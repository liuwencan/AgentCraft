package cn.lwccxy.agentcraft.service;

import cn.lwccxy.agentcraft.entity.record.FileInfo;
import cn.lwccxy.agentcraft.entity.AiFileInfo;

import java.util.List;

/**
 * 文件信息服务接口
 */
public interface FileInfoService {

    /**
     * 保存文件信息
     */
    void saveFileInfo(FileInfo fileInfo);

    /**
     * 根据文件ID获取文件信息
     */
    FileInfo getFileInfoById(String fileId);

    /**
     * 根据文件ID获取数据库实体
     */
    AiFileInfo getEntityById(String fileId);

    /**
     * 更新文件信息
     */
    void updateFileInfo(FileInfo fileInfo);

    /**
     * 删除文件信息
     */
    void deleteFileInfo(String fileId);

    /**
     * 检查文件是否存在
     */
    boolean exists(String fileId);

    /**
     * 获取所有文件列表
     */
    List<FileInfo> getAllFiles();

    /**
     * 获取文件数量
     */
    int getFileCount();
}
