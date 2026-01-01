package cn.lwccxy.agentcraft.service;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.record.pptx.PptInstStatus;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * AI PPT 实例服务接口
 */
public interface AiPptInstService extends IService<AiPptInst> {

    /**
     * 创建新的PPT实例
     *
     * @param conversationId 会话ID
     * @param query          用户原始需求
     * @return PPT实例
     */
    AiPptInst createInst(String conversationId, String query);

    /**
     * 根据会话ID获取最新的PPT实例
     *
     * @param conversationId 会话ID
     * @return 最新的PPT实例
     */
    AiPptInst getLatestInst(String conversationId);

    /**
     * 根据会话ID获取所有PPT实例
     *
     * @param conversationId 会话ID
     * @return PPT实例列表
     */
    List<AiPptInst> getInstsByConversationId(String conversationId);

    /**
     * 根据会话ID获取已完成的PPT实例
     *
     * @param conversationId 会话ID
     * @return 已完成的PPT实例列表
     */
    List<AiPptInst> getCompletedInsts(String conversationId);

    /**
     * 更新PPT实例状态
     *
     * @param id     实例ID
     * @param status 新状态
     * @return 是否更新成功
     */
    boolean updateStatus(Long id, PptInstStatus status);

    /**
     * 更新需求
     *
     * @param id          实例ID
     * @param requirement 需求
     * @param status      状态
     * @return 是否更新成功
     */
    boolean updateRequirement(Long id, String requirement, PptInstStatus status);

    /**
     * 更新搜索信息
     *
     * @param id        实例ID
     * @param searchInfo 搜索信息
     * @param status    状态
     * @return 是否更新成功
     */
    boolean updateSearchInfo(Long id, String searchInfo, PptInstStatus status);

    /**
     * 更新大纲
     *
     * @param id     实例ID
     * @param outline 大纲
     * @param status 状态
     * @return 是否更新成功
     */
    boolean updateOutline(Long id, String outline, PptInstStatus status);

    /**
     * 更新模板编码
     *
     * @param id           实例ID
     * @param templateCode 模板编码
     * @param status       状态
     * @return 是否更新成功
     */
    boolean updateTemplateCode(Long id, String templateCode, PptInstStatus status);

    /**
     * 更新PPT Schema
     *
     * @param id        实例ID
     * @param pptSchema PPT Schema JSON
     * @param status    状态
     * @return 是否更新成功
     */
    boolean updatePptSchema(Long id, String pptSchema, PptInstStatus status);

    /**
     * 更新文件URL
     *
     * @param id      实例ID
     * @param fileUrl 文件URL
     * @param status  状态
     * @return 是否更新成功
     */
    boolean updateFileUrl(Long id, String fileUrl, PptInstStatus status);

    /**
     * 更新错误信息
     *
     * @param id       实例ID
     * @param errorMsg 错误信息
     * @param status   状态
     * @return 是否更新成功
     */
    boolean updateError(Long id, String errorMsg, PptInstStatus status);
}
