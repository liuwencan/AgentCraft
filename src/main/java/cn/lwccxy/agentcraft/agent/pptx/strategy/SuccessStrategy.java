package cn.lwccxy.agentcraft.agent.pptx.strategy;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.record.pptx.PptInstStatus;
import cn.lwccxy.agentcraft.entity.record.pptx.PptSchema;
import cn.lwccxy.agentcraft.entity.vo.UpdateAnswerRequest;
import cn.lwccxy.agentcraft.prompts.PptBuilderPrompts;
import cn.lwccxy.agentcraft.utils.ThinkTagParser;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * 成功状态策略
 */
@Slf4j
public class SuccessStrategy implements PptStateStrategy {

    private static final PptInstStatus TARGET_STATUS = PptInstStatus.SUCCESS;

    @Override
    public void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                        StringBuilder thinkingBuffer, PptStateStrategyContext context) {
        String fileUrl = inst.getFileUrl();
        final int pageCount = getPageCount(inst);

        String prompt;

        // 根据是否为修改操作使用不同的提示词
        if (context.isModifyMode()) {
            // 修改操作使用修改总结提示词，从 Context 获取当前修改需求
            String modifyRequest = context.getModifyQuery();
            prompt = PptBuilderPrompts.getModifySummaryPrompt(modifyRequest, fileUrl);
        } else {
            // 创建操作使用创建总结提示词
            String requirement = inst.getRequirement();
            prompt = PptBuilderPrompts.getSummaryPrompt(requirement, fileUrl, pageCount);
        }

        StringBuilder llmResponse = new StringBuilder();

        Disposable disposable = context.getChatClient().prompt()
                .messages(new UserMessage(prompt))
                .stream()
                .content()
                .doOnNext(chunk -> {
                    sink.tryEmitNext(context.createTextResponse(chunk));
                    llmResponse.append(chunk);
                })
                .doOnComplete(() -> {
                    // 保存大模型返回的内容（去除think标签）
                    saveResultToSession(context, inst,
                            ThinkTagParser.stripThinkTags(llmResponse.toString()),
                            thinkingBuffer);
                    sink.tryEmitComplete();
                    // 清理修改模式标记和修改需求
                    context.setModifyMode(false);
                    context.setModifyQuery(null);
                })
                .doOnError(err -> {
                    log.error("总结生成异常", err);
                    sink.tryEmitError(err);
                    // 清理修改模式标记和修改需求
                    context.setModifyMode(false);
                    context.setModifyQuery(null);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        context.setDisposable(inst.getConversationId(), disposable);
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }

    private int getPageCount(AiPptInst inst) {
        if (inst.getPptSchema() == null) {
            return 0;
        }
        PptSchema pptSchema = JSON.parseObject(inst.getPptSchema(), PptSchema.class);
        return pptSchema.getSlides() == null ? 0 : pptSchema.getSlides().size();
    }

    /**
     * 保存结果到会话
     */
    private void saveResultToSession(PptStateStrategyContext context, AiPptInst inst,
                                      String result, StringBuilder thinkingBuffer) {
        if (context.getSessionService() == null || context.getCurrentSessionId() == null) {
            return;
        }

        try {
            UpdateAnswerRequest request = UpdateAnswerRequest.builder()
                    .id(context.getCurrentSessionId())
                    .answer(result)
                    .thinking(thinkingBuffer.toString())
                    .build();
            context.getSessionService().updateAnswer(request);
            String conversationId = inst != null ? inst.getConversationId() : context.getCurrentConversationId();
            log.info("PPT生成结果已保存到会话: conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("保存结果到会话失败", e);
        }
    }
}
