package cn.lwccxy.agentcraft.entity.record;

import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;

import static java.util.Collections.synchronizedList;

/**
 * Agent 轮次执行状态
 * 保存每轮执行时的中间状态
 */
@Data
public class RoundState {
    /** 当前运行模式 */
    public RoundMode mode = RoundMode.UNKNOWN;

    /** 文本缓冲区 */
    public StringBuilder textBuffer = new StringBuilder();

    /** 工具调用列表 */
    public List<AssistantMessage.ToolCall> toolCalls = synchronizedList(new java.util.ArrayList<>());

    /** ThinkTagParser 的 inThink 状态，跨 chunk 追踪 <think/> 标签 */
    public boolean inThink = false;

}
