package cn.lwccxy.agentcraft.entity.event;

/**
 * Agent 流式事件。
 *
 * @author rel
 */
public sealed interface AgentStreamEvent permits
        AgentStreamEvent.Thinking,
        AgentStreamEvent.Text,
        AgentStreamEvent.ToolStart,
        AgentStreamEvent.ToolEnd,
        AgentStreamEvent.Error,
        AgentStreamEvent.Complete {

    /**
     * LLM 思考过程
     */
    record Thinking(String content) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"thinking\",\"content\":" + escapeJson(content) + "}";
        }
    }

    /**
     * LLM 正常文本输出。
     */
    record Text(String content) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"text\",\"content\":" + escapeJson(content) + "}";
        }
    }

    /**
     * 工具即将执行。
     */
    record ToolStart(String toolName, String toolCallId, String arguments) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"tool_start\",\"toolName\":" + escapeJson(toolName)
                    + ",\"toolCallId\":" + escapeJson(toolCallId)
                    + ",\"arguments\":" + escapeJson(arguments) + "}";
        }
    }

    /**
     * 工具执行完成。
     */
    record ToolEnd(String toolName, String toolCallId, String result) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"tool_end\",\"toolName\":" + escapeJson(toolName)
                    + ",\"toolCallId\":" + escapeJson(toolCallId)
                    + ",\"result\":" + escapeJson(result) + "}";
        }
    }

    /**
     * 错误事件。
     */
    record Error(String code, String message, String detail) implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"error\",\"code\":" + escapeJson(code)
                    + ",\"message\":" + escapeJson(message)
                    + ",\"detail\":" + escapeJson(detail) + "}";
        }
    }

    /**
     * Agent 执行完成。
     */
    record Complete() implements AgentStreamEvent {
        @Override
        public String toJSON() {
            return "{\"type\":\"complete\"}";
        }
    }

    /**
     * 序列化为 JSON 字符串。
     */
    String toJSON();

    /**
     * JSON 字符串转义。
     */
    static String escapeJson(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
