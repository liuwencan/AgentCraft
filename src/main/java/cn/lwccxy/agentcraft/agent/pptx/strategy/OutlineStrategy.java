package cn.lwccxy.agentcraft.agent.pptx.strategy;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.record.pptx.AiPptTemplate;
import cn.lwccxy.agentcraft.entity.record.pptx.PptInstStatus;
import cn.lwccxy.agentcraft.prompts.PptBuilderPrompts;
import cn.lwccxy.agentcraft.utils.ThinkTagParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Sinks;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

/**
 * 大纲生成策略
 */
@Slf4j
public class OutlineStrategy implements PptStateStrategy {

    private static final PptInstStatus TARGET_STATUS = PptInstStatus.SCHEMA;

    @Override
    public void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                        StringBuilder thinkingBuffer, PptStateStrategyContext context) {
        sink.tryEmitNext(context.createThinkingResponse("正在生成PPT大纲...\n"));

        String requirement = inst.getRequirement();
        String searchInfo = inst.getSearchInfo();
        String templateCode = inst.getTemplateCode();
        AiPptTemplate template = context.getPptTemplateService().getByCode(templateCode);

        if (template == null) {
            log.error("模板不存在: templateCode={}", templateCode);
            // 失败时不回退状态，只更新错误信息，转到 FAILED
            context.getPptInstService().updateError(inst.getId(),
                    "模板不存在: " + templateCode, PptInstStatus.TEMPLATE);
            // 转到 FAILED 策略
            PptStateStrategyFactory.getInstance().executeFailedState(inst, sink, query, thinkingBuffer, context);
            return;
        }

        // 根据模板的schema和搜索信息来生成大纲
        String templateSchema = template.getTemplateSchema();
        String prompt = PptBuilderPrompts.getOutlinePrompt(requirement, templateSchema, template.getTemplateName(), searchInfo);

        StringBuilder outlineContent = new StringBuilder();

        Disposable disposable = context.getChatClient().prompt()
                .messages(new UserMessage(prompt))
                .stream()
                .content()
                .doOnNext(chunk -> {
                    sink.tryEmitNext(context.createThinkingResponse(chunk));
                    outlineContent.append(chunk);
                })
                .doOnComplete(() -> {
                    log.info("大纲生成完成");
                    context.getPptInstService().updateOutline(inst.getId(),
                            ThinkTagParser.stripThinkTags(outlineContent.toString()),
                            TARGET_STATUS);
                    sink.tryEmitNext(context.createThinkingResponse("\n✅ 大纲生成完成，开始设计PPT详细内容\n"));
                    context.continueStateMachine(inst, sink, query, thinkingBuffer);
                })
                .doOnError(err -> {
                    log.error("大纲生成异常", err);
                    // 失败时不回退状态，只更新错误信息，转到 FAILED
                    context.getPptInstService().updateError(inst.getId(),
                            "大纲生成失败: " + err.getMessage(), PptInstStatus.OUTLINE);
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
}
