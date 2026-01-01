package cn.lwccxy.agentcraft.agent.skills.manual.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 技能元数据。
 * 包含技能的基本信息，用于在系统提示中展示技能列表。
 * 完整的技能内容通过 SkillRegistry 按需加载。
 */
public record SkillMetadata(
        String name,
        String description,
        Path skillPath,
        SkillSource source,
        List<String> allowedTools,
        Path skillFile
) {

    public enum SkillSource {
        /**
         * 项目技能目录
         */
        PROJECT,
        /**
         * 用户技能目录
         */
        USER
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isToolAllowed(String toolName) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return true;
        }
        if (allowedTools.contains(toolName)) {
            return true;
        }
        for (String pattern : allowedTools) {
            if (matchesPattern(toolName, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPattern(String toolName, String pattern) {
        int parenIndex = pattern.indexOf('(');
        if (parenIndex > 0 && pattern.endsWith(")")) {
            String prefix = pattern.substring(0, parenIndex);
            String subPattern = pattern.substring(parenIndex + 1, pattern.length() - 1);
            if (toolName.startsWith(prefix + "(")) {
                String toolSub = toolName.substring(prefix.length() + 1, toolName.length() - 1);
                return matchesGlobPattern(toolSub, subPattern);
            }
            return false;
        }
        if (pattern.endsWith(":*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return toolName.startsWith(prefix) && toolName.charAt(prefix.length()) == ':';
        }
        return pattern.equals(toolName);
    }

    private static boolean matchesGlobPattern(String text, String pattern) {
        if (pattern.equals("*")) return true;
        String[] parts = pattern.split("\\*");
        int lastIndex = 0;
        for (String part : parts) {
            int index = text.indexOf(part, lastIndex);
            if (index == -1) return false;
            lastIndex = index + part.length();
        }
        if (!pattern.endsWith("*")) {
            String lastPart = parts[parts.length - 1];
            return text.endsWith(lastPart);
        }
        return true;
    }

    public static class Builder {
        private String name;
        private String description;
        private Path skillPath;
        private SkillSource source;
        private List<String> allowedTools;
        private Path skillFile;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder skillPath(Path skillPath) {
            this.skillPath = skillPath;
            return this;
        }

        public Builder source(SkillSource source) {
            this.source = source;
            return this;
        }

        public Builder allowedTools(List<String> allowedTools) {
            this.allowedTools = allowedTools;
            return this;
        }

        public Builder skillFile(Path skillFile) {
            this.skillFile = skillFile;
            return this;
        }

        public SkillMetadata build() {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(description, "description must not be null");
            Objects.requireNonNull(skillPath, "skillPath must not be null");
            Objects.requireNonNull(source, "source must not be null");
            return new SkillMetadata(name, description, skillPath, source, allowedTools, skillFile);
        }
    }
}
