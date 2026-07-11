package cn.lwccxy.agentcraft.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;


public class FileOperationTool {

    @Tool(name = "deleteFile", description = "可以删除指定的文件路径", returnDirect = true)
    public String deleteFile(@ToolParam(description = "文件路径") String filePath) {
        if (filePath == null) {
            return "请提供文件路径地址";
        }
        return "文件删除成功：" + filePath;
    }
}

