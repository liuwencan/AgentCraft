package cn.lwccxy.agentcraft.agent.skills.manual;

import cn.lwccxy.agentcraft.agent.skills.manual.config.SkillConfig;
import cn.lwccxy.agentcraft.agent.skills.manual.model.SkillLoadingException;
import cn.lwccxy.agentcraft.agent.skills.manual.model.SkillMetadata;
import cn.lwccxy.agentcraft.agent.skills.manual.registry.FileSystemSkillRegistry;
import cn.lwccxy.agentcraft.agent.skills.manual.registry.SkillRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 技能管理器。
 *
 * 统一管理技能相关的所有逻辑，包括：
 * - 获取技能列表
 * - 格式化技能提示
 * - 读取技能内容
 *
 * @author rel
 * 
 */
public class SkillManager {

    private static final Logger log = LoggerFactory.getLogger(SkillManager.class);

    public static final String READ_SKILL_TOOL_NAME = "read_skill";

    private final SkillConfig config;
    private final ObjectMapper objectMapper;
    private final SkillRegistry registry;
    private final Function<List<SkillMetadata>, String> promptFormatter;

    public static SkillManager create(SkillConfig config) {
        if (config == null) {
            return null;
        }
        return new SkillManager(config);
    }

    private SkillManager(SkillConfig config) {
        this.config = config;
        this.registry = buildSkillRegistry(config);
        this.promptFormatter = config.getPromptFormatter() != null
                ? config.getPromptFormatter()
                : SkillPromptFormatter::format;
        this.objectMapper = new ObjectMapper();
    }

    private static SkillRegistry buildSkillRegistry(SkillConfig config) {
        FileSystemSkillRegistry.Builder builder = FileSystemSkillRegistry.builder();
        for (var dirPath : config.getDirectories()) {
            builder.addDirectory(dirPath);
        }
        return builder.autoReload(config.isAutoReload()).build();
    }

    public List<SkillMetadata> getSkills() {
        try {
            return new ArrayList<>(registry.listAll());
        } catch (SkillLoadingException e) {
            log.error("Failed to load skills", e);
            return List.of();
        }
    }

    public SkillMetadata getSkill(String name) {
        try {
            return registry.get(name);
        } catch (SkillLoadingException e) {
            log.error("Failed to get skill: {}", name, e);
            return null;
        }
    }

    public String readSkillContent(String name) {
        try {
            return registry.readSkillContent(name);
        } catch (SkillLoadingException e) {
            log.error("Failed to read skill content: {}", name, e);
            return null;
        }
    }

    public String formatPrompt() {
        List<SkillMetadata> skills = getSkills();
        if (skills.isEmpty()) {
            return "";
        }
        return promptFormatter.apply(skills);
    }

    public boolean isEnabled() {
        return config != null;
    }

    public boolean containsSkill(String name) {
        return registry.contains(name);
    }

    public int getSkillCount() {
        return registry.size();
    }

    public void clearCache() {
        registry.clearCache();
    }

    public void reload() {
        if (config.isAutoReload()) {
            registry.reload();
        }
    }

    public SkillConfig getConfig() {
        return config;
    }

    public SkillRegistry getRegistry() {
        return registry;
    }

    // ==================== 消息构建相关方法 ====================

    public String extractSkillName(String arguments) {
        try {
            var jsonNode = objectMapper.readTree(arguments);
            if (jsonNode.has("skill")) {
                return jsonNode.get("skill").asText();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    public String extractSkillContent(String result) {
        try {
            var jsonNode = objectMapper.readTree(result);
            if (jsonNode.has("success") && jsonNode.get("success").asBoolean()) {
                if (jsonNode.has("content")) {
                    return jsonNode.get("content").asText();
                }
            }
            return null;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse read_skill result: {}", result, e);
            return null;
        }
    }

    public String stripYamlFrontmatter(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String trimmed = content.trim();
        if (!trimmed.startsWith("---")) {
            return content;
        }

        int endIndex = trimmed.indexOf("\n---", 4);
        if (endIndex == -1) {
            endIndex = trimmed.indexOf("\r\n---", 4);
        }

        if (endIndex == -1) {
            return content;
        }

        int contentStart = endIndex + 4;
        if (trimmed.charAt(endIndex + 3) == '\r') {
            contentStart = endIndex + 5;
        }

        int actualStart = trimmed.indexOf('\n', contentStart);
        if (actualStart == -1) {
            return trimmed.substring(contentStart).trim();
        }

        return trimmed.substring(actualStart + 1).trim();
    }

    public List<Message> buildSkillMessages(String toolCallId, String skillContent, String skillName) {
        List<Message> messages = new ArrayList<>();
        String cleanedContent = stripYamlFrontmatter(skillContent);

        ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                toolCallId, READ_SKILL_TOOL_NAME, cleanedContent);
        messages.add(ToolResponseMessage.builder()
                .responses(List.of(tr))
                .build());

        messages.add(new UserMessage(String.format(
                "【技能已加载】技能 \"%s\" 的内容已在上面的工具返回中。\n" +
                "【重要】禁止再次调用 read_skill(\"%s\")。\n" +
                "【下一步】请直接按照上面返回的技能指令来完成任务。",
                skillName, skillName)));

        log.debug("Skill loaded: {} with {} chars", skillName, cleanedContent.length());
        return messages;
    }
}
