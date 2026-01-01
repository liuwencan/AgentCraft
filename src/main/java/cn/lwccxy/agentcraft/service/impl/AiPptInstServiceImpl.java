package cn.lwccxy.agentcraft.service.impl;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.record.pptx.PptInstStatus;
import cn.lwccxy.agentcraft.mapper.AiPptInstMapper;
import cn.lwccxy.agentcraft.service.AiPptInstService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI PPT 实例服务实现
 */
@Slf4j
@Service
public class AiPptInstServiceImpl extends ServiceImpl<AiPptInstMapper, AiPptInst> implements AiPptInstService {

    @Override
    public AiPptInst createInst(String conversationId, String query) {
        AiPptInst inst = AiPptInst.builder()
                .conversationId(conversationId)
                .query(query)
                .status(PptInstStatus.INIT.getCode())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        save(inst);
        log.info("创建PPT实例: id={}, conversationId={}", inst.getId(), conversationId);
        return inst;
    }

    @Override
    public AiPptInst getLatestInst(String conversationId) {
        LambdaQueryWrapper<AiPptInst> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiPptInst::getConversationId, conversationId)
                .orderByDesc(AiPptInst::getCreateTime)
                .last("LIMIT 1");
        return getOne(wrapper);
    }

    @Override
    public List<AiPptInst> getInstsByConversationId(String conversationId) {
        LambdaQueryWrapper<AiPptInst> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiPptInst::getConversationId, conversationId)
                .orderByDesc(AiPptInst::getCreateTime);
        return list(wrapper);
    }

    @Override
    public List<AiPptInst> getCompletedInsts(String conversationId) {
        LambdaQueryWrapper<AiPptInst> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiPptInst::getConversationId, conversationId)
                .eq(AiPptInst::getStatus, PptInstStatus.SUCCESS.getCode())
                .orderByDesc(AiPptInst::getCreateTime);
        return list(wrapper);
    }

    @Override
    public boolean updateStatus(Long id, PptInstStatus status) {
        AiPptInst inst = new AiPptInst();
        inst.setId(id);
        inst.setStatus(status.getCode());
        inst.setUpdateTime(LocalDateTime.now());
        return updateById(inst);
    }

    @Override
    public boolean updateRequirement(Long id, String requirement, PptInstStatus status) {
        AiPptInst inst = new AiPptInst();
        inst.setId(id);
        inst.setRequirement(requirement);
        inst.setStatus(status.getCode());
        inst.setUpdateTime(LocalDateTime.now());
        return updateById(inst);
    }

    @Override
    public boolean updateSearchInfo(Long id, String searchInfo, PptInstStatus status) {
        AiPptInst inst = new AiPptInst();
        inst.setId(id);
        inst.setSearchInfo(searchInfo);
        inst.setStatus(status.getCode());
        inst.setUpdateTime(LocalDateTime.now());
        return updateById(inst);
    }

    @Override
    public boolean updateOutline(Long id, String outline, PptInstStatus status) {
        AiPptInst inst = new AiPptInst();
        inst.setId(id);
        inst.setOutline(outline);
        inst.setStatus(status.getCode());
        inst.setUpdateTime(LocalDateTime.now());
        return updateById(inst);
    }

    @Override
    public boolean updateTemplateCode(Long id, String templateCode, PptInstStatus status) {
        AiPptInst inst = new AiPptInst();
        inst.setId(id);
        inst.setTemplateCode(templateCode);
        inst.setStatus(status.getCode());
        inst.setUpdateTime(LocalDateTime.now());
        return updateById(inst);
    }

    @Override
    public boolean updatePptSchema(Long id, String pptSchema, PptInstStatus status) {
        AiPptInst inst = new AiPptInst();
        inst.setId(id);
        inst.setPptSchema(pptSchema);
        inst.setStatus(status.getCode());
        inst.setUpdateTime(LocalDateTime.now());
        return updateById(inst);
    }

    @Override
    public boolean updateFileUrl(Long id, String fileUrl, PptInstStatus status) {
        AiPptInst inst = new AiPptInst();
        inst.setId(id);
        inst.setFileUrl(fileUrl);
        inst.setStatus(status.getCode());
        inst.setUpdateTime(LocalDateTime.now());
        return updateById(inst);
    }

    @Override
    public boolean updateError(Long id, String errorMsg, PptInstStatus status) {
        AiPptInst inst = new AiPptInst();
        inst.setId(id);
        inst.setErrorMsg(errorMsg);
        inst.setStatus(status.getCode());
        inst.setUpdateTime(LocalDateTime.now());
        return updateById(inst);
    }
}
