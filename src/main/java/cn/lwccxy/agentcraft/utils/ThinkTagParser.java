package cn.lwccxy.agentcraft.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * &lt;think/&gt; 标签解析器。
 *
 * 无状态工具类，将 LLM 流式输出的文本 chunk 拆分为思考内容和正常文本。
 * 支持跨 chunk 的标签状态追踪（通过 inThink 参数）。
 *
 * @author rel
 */
public final class ThinkTagParser {

    private static final String THINK_START = "<think";
    private static final String THINK_END = "</think";

    private ThinkTagParser() {
    }

    /**
     * 去除文本中的 think标签及其内容。
     *
     * @param input 可能包含 think 标签的文本
     * @return 去除 think 标签后的文本
     */
    public static String stripThinkTags(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // 通用正则：匹配 <think...>...</think...>（兼容空格、属性、自闭合等变体）
        String result = input.replaceAll("(?s)<think[^>]*>.*?</think[^>]*>", "").trim();
        return result;
    }

    /**
     * 内容段，标识是思考内容还是正常文本。
     */
    public record Segment(boolean thinking, String content) {
    }

    /**
     * 解析结果。
     */
    public record ParseResult(List<Segment> segments, boolean inThink) {
    }

    /**
     * 解析一个文本 chunk。
     *
     * @param chunk   当前文本 chunk
     * @param inThink 上一个 chunk 结束时的 think 标签内状态
     * @return 解析结果，包含拆分后的内容段和更新后的 inThink 状态
     */
    public static ParseResult parse(String chunk, boolean inThink) {
        if (chunk == null || chunk.isEmpty()) {
            return new ParseResult(List.of(), inThink);
        }

        List<Segment> segments = new ArrayList<>();
        boolean currentInThink = inThink;
        int index = 0;

        while (index < chunk.length()) {
            int thinkStartIdx = chunk.indexOf(THINK_START, index);
            int thinkEndIdx = chunk.indexOf(THINK_END, index);

            int nextTagPos;
            boolean isStartTag;

            if (thinkStartIdx == -1 && thinkEndIdx == -1) {
                String remaining = chunk.substring(index);
                if (!remaining.isEmpty()) {
                    segments.add(new Segment(currentInThink, remaining));
                }
                break;
            }

            if (thinkStartIdx != -1 && (thinkEndIdx == -1 || thinkStartIdx < thinkEndIdx)) {
                nextTagPos = thinkStartIdx;
                isStartTag = true;
            } else {
                nextTagPos = thinkEndIdx;
                isStartTag = false;
            }

            if (nextTagPos > index) {
                String beforeTag = chunk.substring(index, nextTagPos);
                if (!beforeTag.isEmpty()) {
                    segments.add(new Segment(currentInThink, beforeTag));
                }
            }

            int tagEnd = chunk.indexOf('>', nextTagPos);
            if (tagEnd == -1) {
                currentInThink = isStartTag;
                break;
            }

            currentInThink = isStartTag;
            index = tagEnd + 1;
        }

        return new ParseResult(segments, currentInThink);
    }
}
