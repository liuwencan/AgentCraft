package cn.lwccxy.agentcraft.service;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptTemplate;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * AI PPT 模板服务接口
 */
public interface AiPptTemplateService extends IService<AiPptTemplate> {

    /**
     * 根据模板编码获取模板
     *
     * @param templateCode 模板编码
     * @return 模板
     */
    AiPptTemplate getByCode(String templateCode);

    /**
     * 获取所有可用模板
     *
     * @return 模板列表
     */
    List<AiPptTemplate> getAllTemplates();

    /**
     * 根据风格标签获取模板
     *
     * @param styleTags 风格标签
     * @return 模板列表
     */
    List<AiPptTemplate> getByStyleTags(String styleTags);
}
