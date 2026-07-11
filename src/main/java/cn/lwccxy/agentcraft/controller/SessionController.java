package cn.lwccxy.agentcraft.controller;

import cn.lwccxy.agentcraft.common.BaseResult;
import cn.lwccxy.agentcraft.context.UserContext;
import cn.lwccxy.agentcraft.entity.AiFileInfo;
import cn.lwccxy.agentcraft.entity.AiSession;
import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.vo.MessageVO;
import cn.lwccxy.agentcraft.entity.vo.PageResult;
import cn.lwccxy.agentcraft.entity.vo.SessionDetailVO;
import cn.lwccxy.agentcraft.entity.vo.SessionListVO;
import cn.lwccxy.agentcraft.mapper.AiFileInfoMapper;
import cn.lwccxy.agentcraft.mapper.AiPptInstMapper;
import cn.lwccxy.agentcraft.mapper.AiSessionMapper;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话管理控制器
 * 提供会话查询、列表、删除等接口。
 * 所有接口都按当前登录用户 users.id 进行数据隔离。
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
        Long userId = UserContext.currentUserId();
        if (userId == null) {
            return BaseResult.newAuthError();
        }
        log.info("查询会话详情: conversationId={}, userId={}", conversationId, userId);

        try {
            // 仅查当前用户的会话
            LambdaQueryWrapper<AiSession> sessionQuery = new LambdaQueryWrapper<AiSession>()
                    .eq(AiSession::getSessionId, conversationId)
                    .eq(AiSession::getUserId, userId)
                    .orderByAsc(AiSession::getCreateTime);

            List<AiSession> sessions = aiSessionService.list(sessionQuery);

            if (sessions.isEmpty()) {
                return BaseResult.newError("会话不存在");
            }

            SessionDetailVO detailVO = SessionDetailVO.builder()
                    .conversationId(conversationId)
                    .agentType(sessions.get(0).getAgentType())
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
    @Operation(summary = "查询会话列表", description = "分页查询当前用户的会话列表")
    public BaseResult<PageResult<SessionListVO>> getSessionList(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "页大小，默认10") @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = UserContext.currentUserId();
        if (userId == null) {
            return BaseResult.newAuthError();
        }
        log.info("查询会话列表: pageNum={}, pageSize={}, userId={}", pageNum, pageSize, userId);

        try {
            Page<AiSession> page = new Page<>(pageNum, pageSize);
            IPage<AiSession> resultPage = aiSessionMapper.selectSessionListWithFirstRecord(page, userId);

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
    @Operation(summary = "删除会话", description = "删除当前用户下的指定会话及其关联数据（ai_file_info 和 ai_ppt_inst）")
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<String> deleteSession(@PathVariable String conversationId) {
        Long userId = UserContext.currentUserId();
        if (userId == null) {
            return BaseResult.newAuthError();
        }
        log.info("删除会话: conversationId={}, userId={}", conversationId, userId);

        try {
            // 先确认该会话属于当前用户
            AiSession session = aiSessionService.getOne(
                    new LambdaQueryWrapper<AiSession>()
                            .eq(AiSession::getSessionId, conversationId)
                            .eq(AiSession::getUserId, userId)
                            .last("LIMIT 1"));

            if (session == null) {
                return BaseResult.newError("会话不存在");
            }

            // 删除关联的 ai_file_info 数据
            aiFileInfoMapper.delete(
                    new LambdaQueryWrapper<AiFileInfo>()
                            .eq(AiFileInfo::getConversationId, conversationId));

            // 删除关联的 ai_ppt_inst 数据
            aiPptInstMapper.delete(
                    new LambdaQueryWrapper<AiPptInst>()
                            .eq(AiPptInst::getConversationId, conversationId));

            // 删除会话记录（按 userId 限定，避免越权）
            aiSessionService.remove(
                    new LambdaQueryWrapper<AiSession>()
                            .eq(AiSession::getSessionId, conversationId)
                            .eq(AiSession::getUserId, userId));

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
