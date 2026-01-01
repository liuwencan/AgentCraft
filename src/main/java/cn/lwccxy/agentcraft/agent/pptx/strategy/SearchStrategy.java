package cn.lwccxy.agentcraft.agent.pptx.strategy;

import cn.lwccxy.agentcraft.agent.deepresearch.SimpleReactAgent;
import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.record.pptx.PptInstStatus;
import cn.lwccxy.agentcraft.prompts.PptBuilderPrompts;
import cn.lwccxy.agentcraft.utils.ThinkTagParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.Disposables;
import reactor.core.publisher.Sinks;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

/**
 * 信息收集策略
 */
@Slf4j
public class SearchStrategy implements PptStateStrategy {
    private static final PptInstStatus TARGET_STATUS = PptInstStatus.TEMPLATE;

    @Override
    public void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                        StringBuilder thinkingBuffer, PptStateStrategyContext context) {
        sink.tryEmitNext(context.createThinkingResponse("正在收集相关信息...\n"));

        String requirement = inst.getRequirement();

        // 构建搜索提示
        String searchPrompt = PptBuilderPrompts.getSearchInfoPrompt(requirement);

        // 获取 SimpleReactAgent 并注入 tavily 搜索工具
        SimpleReactAgent agent = SimpleReactAgent.builder()
                .chatModel(context.getChatModel())
                .tools(context.getToolCallbacks())
                .build();

        // 流式输出搜索过程
        StringBuilder searchResultBuffer = new StringBuilder();

        Disposable disposable = agent.stream(searchPrompt)
                .doOnNext(chunk -> {
                    searchResultBuffer.append(chunk);
                    sink.tryEmitNext(context.createThinkingResponse(chunk));
                })
                .doOnComplete(() -> {
                    log.info("信息收集完成，结果长度: {}", searchResultBuffer.length());
                    String searchResult = searchResultBuffer.toString();
                    // 去除think标签
                    searchResult = ThinkTagParser.stripThinkTags(searchResult);
                    // 过滤掉工具调用标记等非内容部分
                    searchResult = cleanSearchResult(searchResult);
                    context.getPptInstService().updateSearchInfo(inst.getId(), searchResult, TARGET_STATUS);
                    sink.tryEmitNext(context.createThinkingResponse("\n✅相关信息收集完成，开始选择模板\n"));
                    context.continueStateMachine(inst, sink, query, thinkingBuffer);
                })
                .doOnError(err -> {
                    log.error("信息收集异常", err);
                    // 失败时不回退状态，只更新错误信息，转到 FAILED
                    context.getPptInstService().updateError(inst.getId(),
                            "信息收集失败: " + err.getMessage(), PptInstStatus.SEARCH);
                    // 转到 FAILED 策略
                    PptStateStrategyFactory.getInstance().executeFailedState(inst, sink, query, thinkingBuffer, context);
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
     * 清理搜索结果，过滤掉工具调用标记等非内容部分
     */
    private String cleanSearchResult(String result) {
        if (result == null || result.trim().isEmpty()) {
            return "";
        }

        // 移除工具调用标记（如 <tool_calls> 等格式）
        String cleaned = result
                // 移除常见的工具调用标记
                .replaceAll("<tool_calls>.*?</tool_calls>", "")
                .replaceAll("\\[Tool Call.*?\\]", "")
                .replaceAll("Tool call:.*?\\n", "")
                .replaceAll("\\[TOOL_CALL\\].*?\\[\\/TOOL_CALL\\]", "")
                // 清理多余的空行
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return cleaned;
    }
}
