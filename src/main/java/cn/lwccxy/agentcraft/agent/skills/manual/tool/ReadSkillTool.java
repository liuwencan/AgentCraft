package cn.lwccxy.agentcraft.agent.skills.manual.tool;

import cn.lwccxy.agentcraft.agent.skills.manual.model.SkillLoadingException;
import cn.lwccxy.agentcraft.agent.skills.manual.registry.SkillRegistry;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

/**
 * 读取技能内容的工具。
 *
 * 当模型需要使用某个技能时，调用此工具获取完整的 SKILL.md 内容。
 * 工具返回的技能内容会被 Agent 自动注入到下一轮对话中。
 *
 * @author rel
 * 
 */
public class ReadSkillTool implements Function<ReadSkillTool.Request, ReadSkillTool.Result> {

    private static final Logger log = LoggerFactory.getLogger(ReadSkillTool.class);

    public static final String DESCRIPTION = """
            加载指定技能的完整内容。

            当你需要某个技能的详细指令时使用此工具。系统提示中的技能列表只包含简要描述。

            【重要】调用此工具后，技能内容会包含 <command-name>...</command-name> 标签。
            当你看到这个标签时，说明技能已加载：
            - 禁止再次调用 read_skill
            - 直接按照技能指令来完成任务
            - 不要把技能名称当作工具来调用

            用法：read_skill(技能名称)

            示例：
            - read_skill("pdf-extractor") - 加载 PDF 提取技能
            - read_skill("code-review") - 加载代码审查技能
            """;

    private final SkillRegistry skillRegistry;

    public ReadSkillTool(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    /**
     * 创建 ToolCallback。
     *
     * @param skillRegistry 技能注册表
     * @return ToolCallback 实例
     */
    public static ToolCallback create(SkillRegistry skillRegistry) {
        return FunctionToolCallback.builder("read_skill", new ReadSkillTool(skillRegistry))
                .description(DESCRIPTION)
                .inputType(Request.class)
                .build();
    }

    @Override
    public Result apply(Request request) {
        String skillName = request.skill();
        log.debug("Loading skill: {}", skillName);

        try {
            String content = skillRegistry.readSkillContent(skillName);
            log.debug("Skill loaded successfully: {} ({} chars)", skillName, content.length());
            return new Result(skillName, content, true, null);
        } catch (SkillLoadingException e) {
            log.error("Failed to load skill: {}", skillName, e);
            return new Result(skillName, null, false, e.getMessage());
        }
    }

    public record Request(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The name of the skill to load")
            String skill
    ) {}

    public record Result(
            @JsonProperty("skill")
            String skill,
            @JsonProperty("content")
            String content,
            @JsonProperty("success")
            boolean success,
            @JsonProperty("error")
            String error
    ) {}
}
