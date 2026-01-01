package cn.lwccxy.agentcraft.service.impl;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptTemplate;
import cn.lwccxy.agentcraft.mapper.AiPptTemplateMapper;
import cn.lwccxy.agentcraft.service.AiPptTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI PPT 模板服务实现
 */
@Slf4j
@Service
public class AiPptTemplateServiceImpl extends ServiceImpl<AiPptTemplateMapper, AiPptTemplate>
        implements AiPptTemplateService {

    @Override
    public AiPptTemplate getByCode(String templateCode) {
        LambdaQueryWrapper<AiPptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiPptTemplate::getTemplateCode, templateCode);
        return getOne(wrapper);
    }

    @Override
    public List<AiPptTemplate> getAllTemplates() {
        LambdaQueryWrapper<AiPptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AiPptTemplate::getCreateTime);
        return list(wrapper);
    }

    @Override
    public List<AiPptTemplate> getByStyleTags(String styleTags) {
        LambdaQueryWrapper<AiPptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(AiPptTemplate::getStyleTags, styleTags);
        return list(wrapper);
    }
}
