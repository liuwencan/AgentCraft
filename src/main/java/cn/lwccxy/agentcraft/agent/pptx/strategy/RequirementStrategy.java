package cn.lwccxy.agentcraft.agent.pptx.strategy;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.record.pptx.PptInstStatus;
import cn.lwccxy.agentcraft.prompts.PptBuilderPrompts;
import cn.lwccxy.agentcraft.utils.ThinkTagParser;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Sinks;
import reactor.core.Disposable;
import lombok.extern.slf4j.Slf4j;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * 需求澄清策略
 */
@Slf4j
public class RequirementStrategy implements PptStateStrategy {

    private static final PptInstStatus TARGET_STATUS = PptInstStatus.SEARCH;

    @Override
    public void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                        StringBuilder thinkingBuffer, PptStateStrategyContext context) {
        sink.tryEmitNext(context.createThinkingResponse("正在分析您的需求...\n"));

        List<Message> messages = new ArrayList<>();
        String prompt = PptBuilderPrompts.REQUIREMENT_PROMPT;

        messages.add(new SystemMessage(prompt));

        // 加载历史记忆
        context.loadChatHistory(inst.getConversationId(), messages, true, true);

        messages.add(new UserMessage("<question>" + query + "</question>"));

        if (context.getChatMemory() != null) {
            context.getChatMemory().add(inst.getConversationId(), new UserMessage(query));
        }

        // 流式输出
        StringBuilder responseBuffer = new StringBuilder();

        String conversationId = inst.getConversationId();

        Disposable disposable = context.getChatClient().prompt()
                .messages(messages)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    responseBuffer.append(chunk);
                    sink.tryEmitNext(context.createThinkingResponse(chunk));
                })
                .doOnComplete(() -> {
                    log.info("需求分析完成: {}", responseBuffer);
                    String response = ThinkTagParser.stripThinkTags(responseBuffer.toString());

                    if (context.shouldContinueToNextStep(response)) {
                        // 信息完整，继续下一步：信息收集
                        context.getPptInstService().updateRequirement(inst.getId(), response, TARGET_STATUS);
                        sink.tryEmitNext(context.createThinkingResponse("\n✅ 需求已确认，开始收集相关信息\n"));
                        context.continueStateMachine(inst, sink, query, thinkingBuffer);
                    } else {
                        // 信息不足，保存当前状态，转到 FAILED 策略统一输出
                        context.getPptInstService().updateRequirement(inst.getId(), response, PptInstStatus.REQUIREMENT);
                        context.getPptInstService().updateError(inst.getId(), "需要补充信息：\n" + response, PptInstStatus.REQUIREMENT);

                        // 保存AI回复到chatMemory
                        if (context.getChatMemory() != null) {
                            context.getChatMemory().add(inst.getConversationId(), new AssistantMessage(response));
                        }
                        // 转到 FAILED 策略
                        PptStateStrategyFactory.getInstance().executeFailedState(inst, sink, query, thinkingBuffer, context);
                    }
                })
                .doOnError(err -> {
                    log.error("需求分析异常", err);
                    // 失败时不回退状态，只更新错误信息，转到 FAILED
                    context.getPptInstService().updateError(inst.getId(),
                            "需求分析失败: " + err.getMessage(), PptInstStatus.REQUIREMENT);
                    // 转到 FAILED 策略
                    PptStateStrategyFactory.getInstance().executeFailedState(inst, sink, query, thinkingBuffer, context);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        // 保存 disposable 到任务管理器，用于停止任务
        context.setDisposable(conversationId, disposable);
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }
}
