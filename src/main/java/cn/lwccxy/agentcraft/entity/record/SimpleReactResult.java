package cn.lwccxy.agentcraft.entity.record;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * SimpleReactAgent 执行结果
 * 包含最终答案和搜索结果列表
 */
@Data
@Builder
@AllArgsConstructor
public class SimpleReactResult {
    /**
     * 最终答案（纯文本）
     */
    private String answer;

    /**
     * 搜索结果列表
     */
    private List<SearchResult> searchResults;

    public List<SearchResult> getSearchResults() {
        return searchResults;
    }
}
