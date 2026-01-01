/**
 * @author rel
 */
package cn.lwccxy.agentcraft.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一的 Agent 业务监控指标。
 * 设计目标：
 * 1. 所有 Agent 共享同一套指标口径。
 * 2. 标签低基数、稳定、可用于企业级告警与容量分析。
 * 3. 同时覆盖请求级、阶段级、工具级、Token 级指标。
 */
@Slf4j
@Component
public class AgentMetrics {

    public static final String STATUS_SUCCESS = "成功";
    public static final String STATUS_FAILURE = "失败";
    public static final String STATUS_CANCELED = "取消";

    public static final String RESULT_SUCCESS = "成功";
    public static final String RESULT_FAILURE = "失败";

    public static final String DIRECTION_INPUT = "输入";
    public static final String DIRECTION_OUTPUT = "输出";
    public static final String DIRECTION_TOTAL = "总量";

    public static final String ERROR_TYPE_NONE = "无";
    public static final String ERROR_TYPE_UNKNOWN = "未知异常";
    public static final String ERROR_TYPE_TOOL_NOT_FOUND = "工具未注册";
    public static final String ERROR_TYPE_RESULT_INVALID = "结果异常";

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicInteger> activeTaskMap = new ConcurrentHashMap<>();

    public AgentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAgentExecution(String agentType, long durationMs, String status) {
        String normalizedStatus = normalizeStatus(status);
        List<Tag> tags = buildAgentTags(agentType, Tag.of("status", normalizedStatus));

        Timer.builder("agent.request.duration")
                .description("智能体请求总耗时")
                .tags(tags)
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(60)
                )
                .register(meterRegistry)
                .record(Math.max(durationMs, 0), TimeUnit.MILLISECONDS);

        Counter.builder("agent.request.count")
                .description("智能体请求总次数")
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    public void recordFirstTokenResponseTime(String agentType, long ttftMs) {
        Timer.builder("agent.first.token.duration")
                .description("智能体首字响应耗时")
                .tags(buildAgentTags(agentType))
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(
                        Duration.ofMillis(300),
                        Duration.ofMillis(800),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10)
                )
                .register(meterRegistry)
                .record(Math.max(ttftMs, 0), TimeUnit.MILLISECONDS);
    }

    public void recordAgentRounds(String agentType, long rounds) {
        if (rounds <= 0) {
            return;
        }

        DistributionSummary.builder("agent.rounds.per.request")
                .description("单次请求经历的回合数")
                .baseUnit("rounds")
                .tags(buildAgentTags(agentType))
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(1, 2, 3, 5, 8, 13)
                .register(meterRegistry)
                .record(rounds);
    }

    public void recordTokenUsage(String agentType, int inputTokens, int outputTokens, int totalTokens) {
        incrementTokenCounter(agentType, DIRECTION_INPUT, inputTokens);
        incrementTokenCounter(agentType, DIRECTION_OUTPUT, outputTokens);
        incrementTokenCounter(agentType, DIRECTION_TOTAL, totalTokens);

        recordTokenSummary(agentType, DIRECTION_INPUT, inputTokens);
        recordTokenSummary(agentType, DIRECTION_OUTPUT, outputTokens);
        recordTokenSummary(agentType, DIRECTION_TOTAL, totalTokens);
    }

    public void incrementActiveTasks(String agentType) {
        AtomicInteger counter = activeTaskMap.computeIfAbsent(agentType, key -> {
            AtomicInteger gaugeValue = new AtomicInteger(0);
            Gauge.builder("agent.active.count", gaugeValue, AtomicInteger::get)
                    .description("当前活跃请求数")
                    .tags(buildAgentTags(key))
                    .register(meterRegistry);
            return gaugeValue;
        });
        counter.incrementAndGet();
    }

    public void decrementActiveTasks(String agentType) {
        AtomicInteger counter = activeTaskMap.get(agentType);
        if (counter == null) {
            return;
        }
        if (counter.decrementAndGet() < 0) {
            counter.set(0);
        }
    }

    public void recordToolCall(String agentType, String toolName, long durationMs, boolean success, Throwable error) {
        String errorType = success ? ERROR_TYPE_NONE : normalizeThrowable(error);
        recordToolCall(agentType, toolName, durationMs, success, errorType);
    }

    public void recordToolCall(String agentType, String toolName, long durationMs, boolean success, String errorType) {
        String result = success ? RESULT_SUCCESS : RESULT_FAILURE;
        String displayToolName = getToolDisplayName(toolName);

        List<Tag> baseTags = buildAgentTags(agentType,
                Tag.of("tool_name", displayToolName),
                Tag.of("result", result));

        Timer.builder("agent.tool.call.duration")
                .description("工具调用耗时")
                .tags(baseTags)
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(300),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10)
                )
                .register(meterRegistry)
                .record(Math.max(durationMs, 0), TimeUnit.MILLISECONDS);

        Counter.Builder counterBuilder = Counter.builder("agent.tool.call.count")
                .description("工具调用总次数")
                .tags(baseTags);

        if (!success) {
            counterBuilder.tag("error_type", normalizeErrorType(errorType));
        }

        counterBuilder.register(meterRegistry).increment();
    }

    public void recordStageExecution(String agentType, String stageName, long durationMs, boolean success) {
        String normalizedStageName = normalizeStageName(stageName);
        String status = success ? STATUS_SUCCESS : STATUS_FAILURE;

        List<Tag> tags = buildAgentTags(agentType,
                Tag.of("stage_name", normalizedStageName),
                Tag.of("status", status));

        Timer.builder("agent.stage.duration")
                .description("智能体阶段耗时")
                .tags(tags)
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(
                        Duration.ofMillis(100),
                        Duration.ofMillis(500),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30)
                )
                .register(meterRegistry)
                .record(Math.max(durationMs, 0), TimeUnit.MILLISECONDS);

        Counter.builder("agent.stage.count")
                .description("智能体阶段执行次数")
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    public String getAgentDisplayName(String agentType) {
        return switch (agentType) {
            case "websearch" -> "网页搜索智能体";
            case "file" -> "文件问答智能体";
            case "skills" -> "通用技能智能体";
            case "plan-execute" -> "深度研究智能体";
            case "pptx" -> "PPT生成智能体";
            default -> agentType == null || agentType.isBlank() ? "未知智能体" : agentType;
        };
    }

    public String getToolDisplayName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "未知工具";
        }

        String normalized = toolName.toLowerCase(Locale.ROOT);
        if (normalized.contains("tavily") || normalized.contains("search")) {
            return "网络搜索";
        }
        if (normalized.contains("loadcontent") || normalized.contains("filecontent")) {
            return "文件内容读取";
        }
        if (normalized.contains("filesystem") || normalized.contains("file_system")) {
            return "文件系统操作";
        }
        if (normalized.contains("grep")) {
            return "文本检索";
        }
        if (normalized.contains("bash") || normalized.contains("shell")) {
            return "终端执行";
        }
        if (normalized.contains("read_skill")) {
            return "技能读取";
        }
        if (normalized.contains("skills")) {
            return "技能检索";
        }
        return toolName;
    }

    private void incrementTokenCounter(String agentType, String direction, int tokens) {
        if (tokens <= 0) {
            return;
        }

        Counter.builder("agent.token.count")
                .description("智能体 Token 总量")
                .baseUnit("tokens")
                .tags(buildAgentTags(agentType, Tag.of("direction", direction)))
                .register(meterRegistry)
                .increment(tokens);
    }

    private void recordTokenSummary(String agentType, String direction, int tokens) {
        if (tokens <= 0) {
            return;
        }

        DistributionSummary.builder("agent.token.per.request")
                .description("单次请求 Token 分布")
                .baseUnit("tokens")
                .tags(buildAgentTags(agentType, Tag.of("direction", direction)))
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(100, 500, 1000, 3000, 8000, 20000, 50000)
                .register(meterRegistry)
                .record(tokens);
    }

    private List<Tag> buildAgentTags(String agentType, Tag... extraTags) {
        List<Tag> tags = new java.util.ArrayList<>();
        tags.add(Tag.of("agent_code", agentType == null || agentType.isBlank() ? "unknown" : agentType));
        tags.add(Tag.of("agent_name", getAgentDisplayName(agentType)));
        if (extraTags != null) {
            tags.addAll(List.of(extraTags));
        }
        return tags;
    }

    private String normalizeStatus(String status) {
        if (STATUS_SUCCESS.equals(status) || STATUS_FAILURE.equals(status) || STATUS_CANCELED.equals(status)) {
            return status;
        }
        return STATUS_FAILURE;
    }

    private String normalizeThrowable(Throwable error) {
        if (error == null) {
            return ERROR_TYPE_UNKNOWN;
        }
        String simpleName = error.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? ERROR_TYPE_UNKNOWN : simpleName;
    }

    private String normalizeErrorType(String errorType) {
        return errorType == null || errorType.isBlank() ? ERROR_TYPE_UNKNOWN : errorType;
    }

    private String normalizeStageName(String stageName) {
        return stageName == null || stageName.isBlank() ? "未命名阶段" : stageName;
    }
}
