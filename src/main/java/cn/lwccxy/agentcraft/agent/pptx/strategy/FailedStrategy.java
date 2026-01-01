package cn.lwccxy.agentcraft.agent.pptx.strategy;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.record.pptx.PptInstStatus;
import cn.lwccxy.agentcraft.entity.vo.UpdateAnswerRequest;
import cn.lwccxy.agentcraft.prompts.PptBuilderPrompts;
import cn.lwccxy.agentcraft.utils.ThinkTagParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

/**
 * 失败状态策略
 */
@Slf4j
public class FailedStrategy implements PptStateStrategy {

    private static final PptInstStatus TARGET_STATUS = PptInstStatus.FAILED;

    @Override
    public void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                        StringBuilder thinkingBuffer, PptStateStrategyContext context) {
        log.info("执行失败状态策略: status={}, errorMsg={}", inst.getStatusEnum(), inst.getErrorMsg());

        // 获取错误信息
        String errorMsg = inst.getErrorMsg();

        // 生成失败说明
        String prompt;
        if (StringUtils.hasText(thinkingBuffer)) {
            String question = """
                    # 上一轮遇到的问题：
                    %s
                                        
                    # 本轮遇到的问题
                    %s
                    """.formatted(errorMsg, thinkingBuffer);

            prompt = PptBuilderPrompts.getFailurePrompt(question);
        } else {
            prompt = PptBuilderPrompts.getFailurePrompt("PPT生成过程中遇到未知错误");
        }

        StringBuilder responseBuffer = new StringBuilder();

        Disposable disposable = context.getChatClient().prompt()
                .messages(new UserMessage(prompt))
                .stream()
                .content()
                .doOnNext(chunk -> {
                    responseBuffer.append(chunk);
                    sink.tryEmitNext(context.createTextResponse(chunk));
                })
                .doOnComplete(() -> {
                    log.info("失败说明输出完成: {}", responseBuffer);
                    saveResultToSession(context, inst,
                            ThinkTagParser.stripThinkTags(responseBuffer.toString()),
                            thinkingBuffer);
                    sink.tryEmitComplete();
                })
                .doOnError(err -> {
                    log.error("输出失败说明异常", err);
                    String fallbackMsg = StringUtils.hasText(errorMsg) ? errorMsg : "PPT生成失败，请重试";
                    sink.tryEmitNext(context.createTextResponse(fallbackMsg));
                    saveResultToSession(context, inst, fallbackMsg, thinkingBuffer);
                    sink.tryEmitComplete();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        // 保存 disposable 到任务管理器，用于停止任务
        context.setDisposable(inst.getConversationId(), disposable);
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
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
            log.info("PPT生成失败结果已保存到会话: conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("保存结果到会话失败", e);
        }
    }
}
