package cn.lwccxy.agentcraft.entity;

import lombok.Data;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

@Data
public class OverAllState {

    private final String conversationId;
    private final String question;
    private final List<Message> messages = new ArrayList<>();
    private int round = 0;
    private String refinedResearchTopic;

    public OverAllState(String conversationId, String question) {
        this.question = question;
        this.conversationId = conversationId;
    }

    public void nextRound() {
        round++;
    }

    public void add(Message m) {
        messages.add(m);
    }

    public int currentChars() {
        return messages.stream()
                .mapToInt(m -> m.getText() == null ? 0 : m.getText().length())
                .sum();
    }

    public void clearMessages() {
        messages.clear();
    }

    /**
     * 渲染完整上下文（过滤历史 Critique，只保留最近一次）
     * 用于 generatePlan 阶段
     */
    public String renderFullContext() {
        // 先找到最近一次 Critique Feedback 的索引
        int lastCritiqueIndex = findLastCritiqueIndex();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            String text = m.getText();

            // 如果这是之前轮次的 Critique Feedback，跳过
            if (i < lastCritiqueIndex && text != null && text.contains("【Critique Feedback】")) {
                continue;
            }

            sb.append("\n\n[").append(m.getMessageType()).append("]\n\n")
                    .append(text);
        }
        return sb.toString();
    }

    /**
     * 提取所有工具执行结果
     * 用于 summarize 阶段生成报告
     */
    public String extractToolResults() {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            String text = m.getText();
            if (text != null && text.contains("【Completed Task Result】")) {
                sb.append(text).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取最近一次批判反馈
     */
    public String getLastCritique() {
        int index = findLastCritiqueIndex();
        if (index >= 0) {
            return messages.get(index).getText();
        }
        return null;
    }

    /**
     * 找到最近一次 Critique Feedback 的索引
     */
    private int findLastCritiqueIndex() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            String text = messages.get(i).getText();
            if (text != null && text.contains("【Critique Feedback】")) {
                return i;
            }
        }
        return -1;
    }
}
