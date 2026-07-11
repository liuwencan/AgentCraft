package cn.lwccxy.agentcraft.service.impl;

import cn.lwccxy.agentcraft.context.UserContext;
import cn.lwccxy.agentcraft.entity.AiSession;
import cn.lwccxy.agentcraft.entity.vo.SaveQuestionRequest;
import cn.lwccxy.agentcraft.entity.vo.UpdateAnswerRequest;
import cn.lwccxy.agentcraft.mapper.AiSessionMapper;
import cn.lwccxy.agentcraft.service.AiSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI会话服务实现类
 */
@Service
public class AiSessionServiceImpl extends ServiceImpl<AiSessionMapper, AiSession> implements AiSessionService {

    @Override
    public List<AiSession> findRecentBySessionId(String sessionId, int maxRecords) {
        return findRecentBySessionId(sessionId, null, maxRecords);
    }

    @Override
    public List<AiSession> findRecentBySessionId(String sessionId, Long userId, int maxRecords) {
        LambdaQueryWrapper<AiSession> queryWrapper = new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getSessionId, sessionId)
                .orderByDesc(AiSession::getCreateTime)
                .last("LIMIT " + maxRecords);
        if (userId != null) {
            queryWrapper.eq(AiSession::getUserId, userId);
        }
        return this.list(queryWrapper);
    }

    @Override
    public AiSession saveQuestion(SaveQuestionRequest request) {
        AiSession aiSession = new AiSession();
        aiSession.setSessionId(request.getSessionId());
        aiSession.setQuestion(request.getQuestion());
        aiSession.setFileid(request.getFileid());
        aiSession.setTools(request.getTools());
        aiSession.setFirstResponseTime(request.getFirstResponseTime());
        aiSession.setCreateTime(LocalDateTime.now());
        aiSession.setUpdateTime(LocalDateTime.now());

        // 自动从 UserContext 写入当前用户的 users.id，调用方无需传入
        // 显式传入的场景（异步任务/离线导入）以 request 中的值为准
        Long userId = request.getUserId() != null
                ? request.getUserId()
                : UserContext.currentUserId();
        if (userId == null) {
            throw new IllegalStateException(
                    "保存会话失败：未获取到当前登录用户 userId，且 request 未显式传入");
        }
        aiSession.setUserId(userId);

        this.save(aiSession);
        return aiSession;
    }

    @Override
    public boolean updateAnswer(UpdateAnswerRequest request) {
        AiSession session = this.getById(request.getId());
        if (session != null) {
            session.setAnswer(request.getAnswer());
            session.setUpdateTime(LocalDateTime.now());
            if (request.getThinking() != null) {
                session.setThinking(request.getThinking());
            }
            if (request.getTools() != null) {
                session.setTools(request.getTools());
            }
            if (request.getReference() != null) {
                session.setReference(request.getReference());
            }
            if (request.getFirstResponseTime() != null) {
                session.setFirstResponseTime(request.getFirstResponseTime());
            }
            if (request.getTotalResponseTime() != null) {
                session.setTotalResponseTime(request.getTotalResponseTime());
            }
            if (request.getRecommend() != null) {
                session.setRecommend(request.getRecommend());
            }
            return this.updateById(session);
        }
        return false;
    }

}
