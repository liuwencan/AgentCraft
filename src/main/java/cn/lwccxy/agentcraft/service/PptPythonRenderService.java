package cn.lwccxy.agentcraft.service;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;

/**
 * PPT Python 渲染服务接口
 */
public interface PptPythonRenderService {

    /**
     * 调用Python脚本渲染PPT
     *
     * @param inst       PPT实例
     * @param pptSchema PPT Schema JSON
     * @return 生成的PPT文件URL
     * @throws Exception 渲染失败时抛出异常
     */
    String renderPpt(AiPptInst inst, String pptSchema) throws Exception;
}
