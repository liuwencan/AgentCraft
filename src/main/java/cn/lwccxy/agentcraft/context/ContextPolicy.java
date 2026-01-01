package cn.lwccxy.agentcraft.context;

import java.util.HashSet;
import java.util.Set;

/**
 * 上下文压缩策略配置。
 *
 * 控制 Agent 循环中上下文压缩的行为，包括 token 阈值、保留数量、保护工具列表等。
 */
public record ContextPolicy(
        int tokenThreshold,
        int keepRecentTools,
        int maxToolLength,
        Set<String> protectedTools
) {

    /** 默认 token 阈值 */
    public static final int DEFAULT_TOKEN_THRESHOLD = 60000;
    /** 默认保留最近工具调用轮数 */
    public static final int DEFAULT_KEEP_RECENT_TOOLS = 4;
    /** 默认工具内容压缩阈值（ToolResponse 和 ToolCall args 统一使用） */
    public static final int DEFAULT_MAX_TOOL_LENGTH = 200;
    /** 内置保护工具 */
    private static final Set<String> BUILTIN_PROTECTED_TOOLS = Set.of("Skill");

    public ContextPolicy {
        Set<String> allProtected = new HashSet<>(BUILTIN_PROTECTED_TOOLS);
        if (protectedTools != null) {
            allProtected.addAll(protectedTools);
        }
        protectedTools = Set.copyOf(allProtected);
    }

    public static ContextPolicy defaults() {
        return new ContextPolicy(
                DEFAULT_TOKEN_THRESHOLD,
                DEFAULT_KEEP_RECENT_TOOLS,
                DEFAULT_MAX_TOOL_LENGTH,
                Set.of()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isProtected(String toolName) {
        return protectedTools.contains(toolName);
    }

    public static class Builder {
        private int tokenThreshold = DEFAULT_TOKEN_THRESHOLD;
        private int keepRecentTools = DEFAULT_KEEP_RECENT_TOOLS;
        private int maxToolLength = DEFAULT_MAX_TOOL_LENGTH;
        private Set<String> protectedTools = Set.of();

        public Builder tokenThreshold(int v) {
            this.tokenThreshold = v;
            return this;
        }

        public Builder keepRecentTools(int v) {
            this.keepRecentTools = v;
            return this;
        }

        public Builder maxToolLength(int v) {
            this.maxToolLength = v;
            return this;
        }

        public Builder protectedTools(String... tools) {
            this.protectedTools = Set.of(tools);
            return this;
        }

        public ContextPolicy build() {
            return new ContextPolicy(
                    tokenThreshold, keepRecentTools,
                    maxToolLength, protectedTools
            );
        }
    }
}
