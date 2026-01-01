package cn.lwccxy.agentcraft.entity.record;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行结果（包含答案、参考链接、使用的工具）
 */
public class ReactAgentResult {
    private final String answer;
    private final List<SearchResult> references;
    private final String tools;

    public ReactAgentResult(String answer, List<SearchResult> references, String tools) {
        this.answer = answer;
        this.references = references != null ? references : new ArrayList<>();
        this.tools = tools;
    }

    public String getAnswer() {
        return answer;
    }

    public List<SearchResult> getReferences() {
        return references;
    }

    public String getTools() {
        return tools;
    }
}
