package cn.lwccxy.agentcraft.agent.pptx.strategy;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.record.pptx.AiPptTemplate;
import cn.lwccxy.agentcraft.entity.record.pptx.PptInstStatus;
import cn.lwccxy.agentcraft.entity.record.TemplateSelectionResult;
import cn.lwccxy.agentcraft.prompts.PptBuilderPrompts;
import cn.lwccxy.agentcraft.utils.ThinkTagParser;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Sinks;

import java.util.List;

/**
 * 模板选择策略
 */
@Slf4j
public class TemplateStrategy implements PptStateStrategy {

    private static final PptInstStatus TARGET_STATUS = PptInstStatus.OUTLINE;

    @Override
    public void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                        StringBuilder thinkingBuffer, PptStateStrategyContext context) {
        sink.tryEmitNext(context.createThinkingResponse("正在设计模板样式...\n"));

        String requirement = inst.getRequirement();

        // 获取所有可用模板
        List<AiPptTemplate> templates = context.getPptTemplateService().getAllTemplates();

        // 构建模板信息字符串
        StringBuilder templatesInfo = new StringBuilder();
        for (AiPptTemplate template : templates) {
            templatesInfo.append(String.format("""
                            --------------------------------
                            template_code: %s
                            模板名称: %s
                            适用风格: %s
                            模板页数: %d
                            模板说明: %s
                            """,
                    template.getTemplateCode(),
                    template.getTemplateName(),
                    template.getStyleTags(),
                    template.getSlideCount(),
                    template.getTemplateDesc()
            ));
        }

        String prompt = PptBuilderPrompts.getTemplateSelectionPrompt(requirement, templatesInfo.toString());

        BeanOutputConverter<TemplateSelectionResult> converter = new BeanOutputConverter<>(
                new ParameterizedTypeReference<>() {
                });

        try {
            String json = ThinkTagParser.stripThinkTags(
                    context.getChatModel().call(new Prompt(prompt)).getResult().getOutput().getText());
            TemplateSelectionResult result = converter.convert(json);

            log.info("模板选择结果: templateCode={}, reason={}", result.getTemplateCode(), result.getReason());

            context.getPptInstService().updateTemplateCode(inst.getId(), result.getTemplateCode(), TARGET_STATUS);
            sink.tryEmitNext(context.createThinkingResponse("✅ 模板设计完成，开始生成大纲\n"));
            context.continueStateMachine(inst, sink, query, thinkingBuffer);
        } catch (Exception e) {
            log.error("模板选择异常", e);
            // 失败时不回退状态，只更新错误信息，转到 FAILED
            context.getPptInstService().updateError(inst.getId(),
                    "模板选择失败: " + e.getMessage(), PptInstStatus.TEMPLATE);
            // 转到 FAILED 策略
            PptStateStrategyFactory.getInstance().executeFailedState(inst, sink, query, thinkingBuffer, context);
        }
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }
}
