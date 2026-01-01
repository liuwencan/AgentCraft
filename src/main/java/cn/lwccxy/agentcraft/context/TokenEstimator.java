package cn.lwccxy.agentcraft.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

/**
 * Token 估算工具。
 * 区分中英文进行差异化估算：
 * - 英文/ASCII：约 4 字符 = 1 token
 * - 中文/CJK：约 1.5 字符 = 1 token
 * 不依赖外部库，轻量级估算。
 */
@Slf4j
public final class TokenEstimator {

    private static final double CHARS_PER_TOKEN_EN = 4.0;
    private static final double CHARS_PER_TOKEN_CJK = 1.5;

    private TokenEstimator() {
    }

    public static int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int cjkCount = 0;
        int nonCjkCount = 0;

        for (Message msg : messages) {
            int[] counts = countChars(msg);
            cjkCount += counts[0];
            nonCjkCount += counts[1];
        }

        int tokens = (int) (cjkCount / CHARS_PER_TOKEN_CJK + nonCjkCount / CHARS_PER_TOKEN_EN);

        log.info("Token estimation: cjkChars={}, nonCjkChars={}, estimatedTokens={}, messages={}",
                cjkCount, nonCjkCount, tokens, messages.size());

        return tokens;
    }

    private static int[] countChars(Message message) {
        int cjk = 0;
        int nonCjk = 0;

        if (message instanceof SystemMessage sm) {
            int[] c = count(sm.getText());
            cjk += c[0]; nonCjk += c[1];
        } else if (message instanceof UserMessage um) {
            int[] c = count(um.getText());
            cjk += c[0]; nonCjk += c[1];
        } else if (message instanceof AssistantMessage am) {
            int[] c = count(am.getText());
            cjk += c[0]; nonCjk += c[1];
            if (am.getToolCalls() != null) {
                for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                    int[] cn = count(tc.name());
                    cjk += cn[0]; nonCjk += cn[1];
                    int[] ca = count(tc.arguments());
                    cjk += ca[0]; nonCjk += ca[1];
                }
            }
        } else if (message instanceof ToolResponseMessage trm) {
            for (ToolResponseMessage.ToolResponse resp : trm.getResponses()) {
                int[] c = count(resp.responseData());
                cjk += c[0]; nonCjk += c[1];
            }
        } else {
            int[] c = count(message.toString());
            cjk += c[0]; nonCjk += c[1];
        }

        return new int[]{cjk, nonCjk};
    }

    private static int[] count(String s) {
        if (s == null || s.isEmpty()) {
            return new int[]{0, 0};
        }
        int cjk = 0;
        int nonCjk = 0;
        for (int i = 0; i < s.length(); i++) {
            if (isCJK(s.charAt(i))) {
                cjk++;
            } else {
                nonCjk++;
            }
        }
        return new int[]{cjk, nonCjk};
    }

    private static boolean isCJK(char ch) {
        return (ch >= '\u4E00' && ch <= '\u9FFF')
                || (ch >= '\u3400' && ch <= '\u4DBF')
                || (ch >= '\uF900' && ch <= '\uFAFF')
                || (ch >= '\u2E80' && ch <= '\u2EFF')
                || (ch >= '\u3000' && ch <= '\u303F')
                || (ch >= '\uFF00' && ch <= '\uFFEF')
                || (ch >= '\u3040' && ch <= '\u309F')
                || (ch >= '\u30A0' && ch <= '\u30FF');
    }
}
