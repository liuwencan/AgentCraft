package cn.lwccxy.agentcraft.tool;

import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具合并工具类。
 *
 * @author rel
 */
public final class ToolMergeUtils {

    private ToolMergeUtils() {
    }

    /**
     * 合并多个工具数组的辅助方法。
     *
     * @param toolArrays 工具数组
     * @return 合并后的工具数组
     */
    @SafeVarargs
    public static ToolCallback[] mergeTools(ToolCallback[]... toolArrays) {
        List<ToolCallback> result = new ArrayList<>();
        if (toolArrays != null) {
            for (ToolCallback[] array : toolArrays) {
                if (array != null) {
                    result.addAll(List.of(array));
                }
            }
        }
        return result.toArray(new ToolCallback[0]);
    }
}
