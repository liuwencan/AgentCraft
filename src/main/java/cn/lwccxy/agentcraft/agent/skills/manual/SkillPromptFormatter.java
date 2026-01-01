package cn.lwccxy.agentcraft.agent.skills.manual;

import cn.lwccxy.agentcraft.agent.skills.manual.model.SkillMetadata;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 技能提示格式化工具。
 *
 * @author rel
 *
 */
public final class SkillPromptFormatter {

    private SkillPromptFormatter() {
    }

    /**
     * 默认格式化器。
     */
    public static String format(List<SkillMetadata> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }

        String skillList = skills.stream()
                .map(s -> "- **" + s.name() + "**：" + s.description())
                .collect(Collectors.joining("\n"));

        return """
                ## 可用技能列表

                【重要说明】技能不是工具！技能是使用指南和指令集合。
                当你需要使用某个技能时，必须先调用 read_skill 工具加载技能内容。
                技能内容加载后，按照技能中的指令来完成任务。

                **可用技能：**
                %s

                **正确的使用流程：**
                1. 用户要求使用某个技能
                2. 调用 read_skill("技能名称") 来获取技能的完整指令
                3. 仔细阅读返回的技能内容
                4. 按照技能中的指令来完成任务
                5. 绝对不要把技能名称当作工具来调用！

                **示例：**
                  用户："使用 pdf 技能"
                  助手：[调用 read_skill("pdf")]
                  工具：返回 PDF 提取指令
                  助手：[按照指令提取 PDF 内容]
                """.formatted(skillList);
    }
}
