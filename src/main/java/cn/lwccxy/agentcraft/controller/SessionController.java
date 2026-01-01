package cn.lwccxy.agentcraft.controller;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.mapper.AiPptInstMapper;
import cn.lwccxy.agentcraft.mapper.AiSessionMapper;
import cn.lwccxy.agentcraft.common.BaseResult;
import cn.lwccxy.agentcraft.entity.AiFileInfo;
import cn.lwccxy.agentcraft.entity.AiSession;
import cn.lwccxy.agentcraft.entity.vo.MessageVO;
import cn.lwccxy.agentcraft.entity.vo.PageResult;
import cn.lwccxy.agentcraft.entity.vo.SessionDetailVO;
import cn.lwccxy.agentcraft.entity.vo.SessionListVO;
import cn.lwccxy.agentcraft.mapper.AiFileInfoMapper;
import cn.lwccxy.agentcraft.service.AiSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话管理控制器
 * 提供会话查询、列表、删除等接口
 */
@RestController
@RequestMapping("/session")
@Tag(name = "会话管理", description = "会话查询、列表、删除等接口")
@Slf4j
public class SessionController {

    @Autowired
    private AiSessionService aiSessionService;

    @Autowired
    private AiFileInfoMapper aiFileInfoMapper;

    @Autowired
    private AiPptInstMapper aiPptInstMapper;

    @Autowired
    private AiSessionMapper aiSessionMapper;

    /**
     * 根据会话ID查询会话详情
     */
    @GetMapping("/{conversationId}")
    @Operation(summary = "查询会话详情", description = "根据conversationId查询会话详情")
    public BaseResult<SessionDetailVO> getSession(@PathVariable String conversationId) {
        log.info("查询会话详情: conversationId={}", conversationId);

        try {
            // 查询会话记录
            LambdaQueryWrapper<AiSession> sessionQuery = new LambdaQueryWrapper<AiSession>()
                    .eq(AiSession::getSessionId, conversationId)
                    .orderByAsc(AiSession::getCreateTime);

            List<AiSession> sessions = aiSessionService.list(sessionQuery);

            if (sessions.isEmpty()) {
                return BaseResult.newError("会话不存在");
            }

            // 获取agent类型（从最新记录获取）
            String agentType = sessions.get(0).getAgentType();

            SessionDetailVO detailVO = SessionDetailVO.builder()
                    .conversationId(conversationId)
                    .agentType(agentType)
                    .fileid(sessions.get(0).getFileid())
                    .messages(sessions.stream()
                            .map(this::convertToMessageVO)
                            .collect(Collectors.toList()))
                    .build();

            return BaseResult.newSuccess(detailVO);

        } catch (Exception e) {
            log.error("查询会话详情失败: conversationId={}", conversationId, e);
            return BaseResult.newError("查询会话详情失败: " + e.getMessage());
        }
    }

    /**
     * 查询会话列表（分页）
     */
    @GetMapping("/list")
    @Operation(summary = "查询会话列表", description = "分页查询会话列表")
    public BaseResult<PageResult<SessionListVO>> getSessionList(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "页大小，默认10") @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("查询会话列表: pageNum={}, pageSize={}", pageNum, pageSize);

        try {
            Page<AiSession> page = new Page<>(pageNum, pageSize);
            IPage<AiSession> resultPage = aiSessionMapper.selectSessionListWithFirstRecord(page);

            List<SessionListVO> sessionList = resultPage.getRecords().stream()
                    .map(session -> SessionListVO.fromAiSession(session, null))
                    .collect(Collectors.toList());

            PageResult<SessionListVO> pageResult = PageResult.<SessionListVO>builder()
                    .pageNum(pageNum)
                    .pageSize(pageSize)
                    .total(resultPage.getTotal())
                    .records(sessionList)
                    .build();

            return BaseResult.newSuccess(pageResult);

        } catch (Exception e) {
            log.error("查询会话列表失败", e);
            return BaseResult.newError("查询会话列表失败: " + e.getMessage());
        }
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{conversationId}")
    @Operation(summary = "删除会话", description = "删除指定会话及其关联数据（ai_file_info和ai_ppt_inst）")
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<String> deleteSession(@PathVariable String conversationId) {
        log.info("删除会话: conversationId={}", conversationId);

        try {
            // 查询会话获取agent类型
            LambdaQueryWrapper<AiSession> sessionQuery = new LambdaQueryWrapper<AiSession>()
                    .eq(AiSession::getSessionId, conversationId)
                    .last("LIMIT 1");
            AiSession session = aiSessionService.getOne(sessionQuery);

            if (session == null) {
                return BaseResult.newError("会话不存在");
            }

            // 删除关联的ai_file_info数据
            LambdaQueryWrapper<AiFileInfo> fileQuery = new LambdaQueryWrapper<AiFileInfo>()
                    .eq(AiFileInfo::getConversationId, conversationId);
            aiFileInfoMapper.delete(fileQuery);

            // 删除关联的ai_ppt_inst数据
            LambdaQueryWrapper<AiPptInst> pptQuery = new LambdaQueryWrapper<AiPptInst>()
                    .eq(AiPptInst::getConversationId, conversationId);
            aiPptInstMapper.delete(pptQuery);

            // 删除会话记录
            LambdaQueryWrapper<AiSession> deleteSessionQuery = new LambdaQueryWrapper<AiSession>()
                    .eq(AiSession::getSessionId, conversationId);
            aiSessionService.remove(deleteSessionQuery);

            return BaseResult.newSuccess("会话删除成功");

        } catch (Exception e) {
            log.error("删除会话失败: conversationId={}", conversationId, e);
            return BaseResult.newError("删除会话失败: " + e.getMessage());
        }
    }

    /**
     * 转换为消息VO
     */
    private MessageVO convertToMessageVO(AiSession session) {
        return MessageVO.builder()
                .id(session.getId())
                .question(session.getQuestion())
                .answer(session.getAnswer())
                .thinking(session.getThinking())
                .tools(session.getTools())
                .reference(session.getReference())
                .createTime(session.getCreateTime())
                .fileid(session.getFileid())
                .recommend(session.getRecommend())
                .build();
    }
}
