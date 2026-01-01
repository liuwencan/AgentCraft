package cn.lwccxy.agentcraft.agent.deepresearch;

import cn.lwccxy.agentcraft.entity.record.AgentState;
import cn.lwccxy.agentcraft.entity.record.RoundMode;
import cn.lwccxy.agentcraft.utils.ThinkTagParser;
import cn.lwccxy.agentcraft.entity.record.SearchResult;
import cn.lwccxy.agentcraft.entity.record.SimpleReactResult;
import cn.lwccxy.agentcraft.prompts.PlanExecutePrompts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class SimpleReactAgent {

    public static final String REACT_AGENT_SYSTEM_PROMPT = """
            ## 角色
            你是一个联网查询助手，擅长用联网查询工具，查询准确的信息，过滤掉无效的广告。
                        
            ## 工具调用规则（极其重要）
            1. 如果需要调用工具：必须使用 OpenAI 官方 ToolCall 结构，并且 **只能通过工具调用字段输出**。
            2. 工具调用时：**禁止在 content 中出现任何形式的工具调用文本**（包括 JSON、<tool_call>、函数名、参数、思考、推理或描述）。
            3. 工具调用消息必须是一次性、原子性输出，不得混杂任何解释或内容。
            4. 工具调用前后不得输出任何多余文字、标签、换行、推理轨迹或说明。
            5. 调用工具时：
               -工具参数必须是有效的JSON
               -参数必须简洁，不超过500个字符
               -切勿包含以前的工具结果、原始内容、HTML或长文本
               -仅包括工具所需的最小控制参数
                        
            ## 工具执行结果
            系统会自动将工具执行结果作为 ToolResponseMessage 注入上下文，你只需读取并决定下一步动作。
                        
            ## 最终答案规则
            1. 如果上下文已经拥有了完成任务的全部信息，则不要再调用任何工具。
            2. 在这种情况下，你必须输出最终自然语言答案，且 **禁止包含任何工具调用格式**。
            3. 最终答案只允许是自然语言，不能包含 JSON、思考过程、reasoning、ToolCall 或伪代码。
                        
            ## 强制要求（必须遵守）
            1. 工具调用消息必须只通过 ToolCall 字段输出，不允许在 content 字段体现工具调用迹象。
            2. 如果本轮没有工具调用，则视为任务完成，你必须输出最终答案。
            3. 不允许重复调用同一个工具（名称 + 参数完全一致），除非工具调用失败。
            4. 禁止输出会干扰工具系统解析的任何结构（如 <reason>、<ToolCall>、函数 JSON、或模型内部思考）。
            5. 如果上下文已经包含了完成任务的全部信息，则不要再调用任何工具。
            """;

    private final String name;
    private final ChatModel chatModel;
    private final List<ToolCallback> tools;
    private final String systemPrompt;
    private ChatClient chatClient;
    private int maxRounds;
    private ChatMemory chatMemory;

    /**
     * 新增 reflection 相关参数
     */
    // 功能增强拦截器
    private List<Advisor> advisors;
    //最大反思轮数
    private int maxReflectionRounds;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SimpleReactAgent(String name, ChatModel chatModel, List<ToolCallback> tools, String systemPrompt, int maxRounds, ChatMemory chatMemory, List<Advisor> advisors, int maxReflectionRounds) {
        this.name = name;
        this.chatModel = chatModel;
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.maxRounds = maxRounds;
        this.chatMemory = chatMemory;

        // 新增 reflection 相关参数
        this.maxReflectionRounds = maxReflectionRounds;
        this.advisors = advisors;
        initChatClient();

        if (this.chatClient == null) {
            throw new IllegalStateException("ChatClient 初始化失败！");
        }
    }

    private void initChatClient() {
        try {
            ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(tools)
                    .internalToolExecutionEnabled(false)
                    .build();

            ChatClient.Builder builder = ChatClient.builder(chatModel);
            if (!CollectionUtils.isEmpty(advisors)) {
                builder.defaultAdvisors(advisors);
            }
            this.chatClient = builder.defaultOptions(toolOptions).defaultToolCallbacks(tools).build();
        } catch (Exception e) {
            throw new RuntimeException("ChatClient 初始化失败：" + e.getMessage(), e);
        }
    }

    /**
     * 非流式输出
     *
     * @param question
     * @return
     */
    public String call(String question) {
        return callInternal(null, question);
    }

    // 带会话记忆
    public String call(String conversationId, String question) {
        return callInternal(conversationId, question);
    }

    public String callInternal(String conversationId, String question) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        boolean useMemory = conversationId != null && chatMemory != null;

        messages.add(new SystemMessage(REACT_AGENT_SYSTEM_PROMPT + "\n\n" + systemPrompt));

        // ===== 加载历史记忆 =====
        if (useMemory) {
            List<Message> history = chatMemory.get(conversationId);
            if (history != null && !history.isEmpty()) {
                messages.addAll(history);
            }
        }

        messages.add(new UserMessage("<question>" + question + "</question>"));

        // 添加记忆
        if (useMemory) {
            chatMemory.add(conversationId, new UserMessage(question));
        }

        int round = 0;

        while (true) {
            round++;
            if (maxRounds > 0 && round > maxRounds) {
                log.warn("=== 达到 maxRounds（{}），强制生成最终答案 ===", maxRounds);
                messages.add(new UserMessage("""
                        你已达到最大推理轮次限制。
                        请基于当前已有的上下文信息，
                        直接给出最终答案。
                        禁止再调用任何工具。
                        如果信息不完整，请合理总结和说明。
                        """));
                String finalText = chatClient.prompt().messages(messages).call().content();
                if (useMemory) {
                    chatMemory.add(conversationId, new AssistantMessage(finalText));
                }
                return finalText;
            }

            ChatClientResponse chatResponse = chatClient
                    .prompt()
                    .messages(messages)
                    .call()
                    .chatClientResponse();

            String aiText = chatResponse.chatResponse().getResult().getOutput().getText();

            AssistantMessage.Builder builder = AssistantMessage.builder().content(aiText);

            // ===== 没有工具调用，视为最终答案 =====
            if (!chatResponse.chatResponse().hasToolCalls()) {
                if (useMemory) {
                    chatMemory.add(conversationId, new AssistantMessage(aiText));
                }
                return aiText;
            }

            // ===== 有工具调用：执行工具 =====
            messages.add(builder.toolCalls(chatResponse.chatResponse().getResult().getOutput().getToolCalls()).build());

            chatResponse.chatResponse()
                    .getResult()
                    .getOutput()
                    .getToolCalls()
                    .forEach(toolCall -> {
                        String toolName = toolCall.name();
                        String argsJson = toolCall.arguments();

                        ToolCallback callback = findTool(toolName);
                        if (callback == null) {
                            addErrorToolResponse(messages, toolCall, "工具未找到：" + toolName);
                            return;
                        }

                        Object result;
                        try {
                            result = callback.call(argsJson);
                            ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(toolCall.id(), toolName, result.toString());

                            messages.add(ToolResponseMessage.builder().responses(List.of(tr)).build());
                        } catch (Exception ex) {
                            addErrorToolResponse(messages, toolCall, "工具执行失败：" + ex.getMessage());
                        }
                    });
        }
    }

    /**
     * 每轮执行的状态标记位
     */
    private static class RoundState {
        RoundMode mode = RoundMode.UNKNOWN;

        StringBuilder textBuffer = new StringBuilder();
        List<AssistantMessage.ToolCall> toolCalls = Collections.synchronizedList(new ArrayList<>());
        /** ThinkTagParser 的 inThink 状态 */
        boolean inThink = false;
    }


    /**
     * 流式输出
     *
     * @param question
     * @return
     */
    public Flux<String> stream(String question) {
        return streamInternal(null, question);
    }

    /**
     * 带会话记忆的流失输出
     *
     * @param conversationId
     * @param question
     * @return
     */
    public Flux<String> stream(String conversationId, String question) {
        return streamInternal(conversationId, question);
    }


    public Flux<String> streamInternal(String conversationId, String question) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        boolean useMemory = conversationId != null && chatMemory != null;

        messages.add(new SystemMessage(REACT_AGENT_SYSTEM_PROMPT + "\n\n" + systemPrompt));

        // ===== 加载历史记忆 =====
        if (useMemory) {
            List<Message> history = chatMemory.get(conversationId);
            if (history != null && !history.isEmpty()) {
                messages.addAll(history);
            }
        }

        messages.add(new UserMessage("<question>" + question + "</question>"));

        // 添加记忆
        if (useMemory) {
            chatMemory.add(conversationId, new UserMessage(question));
        }

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        // 迭代轮次
        AtomicLong roundCounter = new AtomicLong(0);
        // 是否发送最终结果标记位
        AtomicBoolean hasSentFinalResult = new AtomicBoolean(false);

        hasSentFinalResult.set(false);
        roundCounter.set(0);

        // 收集最终答案，存储memory
        StringBuilder finalAnswerBuffer = new StringBuilder();

        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId);

        return sink.asFlux()
                // 收集最终答案
                .doOnNext(finalAnswerBuffer::append)
                .doOnCancel(() -> hasSentFinalResult.set(true))
                .doFinally(signalType -> {
                    log.info("最终答案: {}", finalAnswerBuffer);
                });
    }

    private void scheduleRound(List<Message> messages, Sinks.Many<String> sink, AtomicLong roundCounter, AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer, boolean useMemory, String conversationId) {
        // 轮次+1
        roundCounter.incrementAndGet();
        RoundState state = new RoundState();

        chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> processChunk(chunk, sink, state))
                .doOnComplete(() -> finishRound(messages, sink, state, roundCounter, hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId))
                .doOnError(err -> {
                    if (!hasSentFinalResult.get()) {
                        hasSentFinalResult.set(true);
                        sink.tryEmitError(err);
                    }
                })
                .subscribe();
    }

    private void processChunk(ChatResponse chunk, Sinks.Many<String> sink, RoundState state) {

        if (chunk == null || chunk.getResult() == null ||
                chunk.getResult().getOutput() == null) {
            return;
        }

        Generation gen = chunk.getResult();
        String text = gen.getOutput().getText();
        List<AssistantMessage.ToolCall> tc = gen.getOutput().getToolCalls();

        // 一旦发现 tool_call，立即进入 TOOL_CALL 模式
        if (tc != null && !tc.isEmpty()) {
            state.mode = RoundMode.TOOL_CALL;

            for (AssistantMessage.ToolCall incoming : tc) {
                mergeToolCall(state, incoming);
            }
            return;
        }

        // 还没出现 tool_call，使用 ThinkTagParser 解析 <think/> 标签
        if (text != null) {
            ThinkTagParser.ParseResult parseResult = ThinkTagParser.parse(text, state.inThink);
            state.inThink = parseResult.inThink();
            for (ThinkTagParser.Segment segment : parseResult.segments()) {
                if (!segment.thinking()) {
                    sink.tryEmitNext(segment.content());
                    state.textBuffer.append(segment.content());
                }
            }
        }
    }

    private void mergeToolCall(RoundState state, AssistantMessage.ToolCall incoming) {

        for (int i = 0; i < state.toolCalls.size(); i++) {
            AssistantMessage.ToolCall existing = state.toolCalls.get(i);

            if (existing.id().equals(incoming.id())) {

                String mergedArgs = Objects.toString(existing.arguments(), "") + Objects.toString(incoming.arguments(), "");

                state.toolCalls.set(i,
                        new AssistantMessage.ToolCall(existing.id(), "function", existing.name(), mergedArgs)
                );
                return;
            }
        }

        // 新 tool call
        state.toolCalls.add(incoming);
    }


    /**
     * 轮次结束处理工具调用
     */
    private void finishRound(List<Message> messages, Sinks.Many<String> sink, RoundState state, AtomicLong roundCounter,
                             AtomicBoolean hasSentFinalResult, StringBuilder finalAnswerBuffer, boolean useMemory, String conversationId) {

        // 如果整轮都没有 tool_call，才是最终答案
        if (state.mode != RoundMode.TOOL_CALL) {
            String finalText = state.textBuffer.toString();
            sink.tryEmitComplete();
            hasSentFinalResult.set(true);

            if (useMemory) {
                chatMemory.add(conversationId, new AssistantMessage(finalText));
            }
            return;
        }

        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
            forceFinalStream(conversationId, useMemory, messages, sink, hasSentFinalResult);
            return;
        }

        // TOOL_CALL
        AssistantMessage assistantMsg = AssistantMessage.builder().toolCalls(state.toolCalls).build();

        messages.add(assistantMsg);

        executeToolCalls(state.toolCalls, messages, hasSentFinalResult, null, () -> {
            if (!hasSentFinalResult.get()) {
                scheduleRound(messages, sink, roundCounter,
                        hasSentFinalResult, finalAnswerBuffer,
                        useMemory, conversationId);
            }
        });
    }


    private void forceFinalStream(String conversationId, boolean useMemory, List<Message> messages, Sinks.Many<String> sink, AtomicBoolean hasSentFinalResult) {
        messages.add(new UserMessage("""
                你已达到最大推理轮次限制。
                请基于当前已有的上下文信息，
                直接给出最终答案。
                禁止再调用任何工具。
                如果信息不完整，请合理总结和说明。
                """));

        StringBuilder stringBuilder = new StringBuilder();
        chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
                        return;
                    }

                    String text = chunk.getResult()
                            .getOutput()
                            .getText();

                    if (text != null && !hasSentFinalResult.get()) {
                        sink.tryEmitNext(text);
                        stringBuilder.append(text);
                    }
                })
                .doOnComplete(() -> {
                    hasSentFinalResult.set(true);
                    sink.tryEmitComplete();
                    if (useMemory) {
                        chatMemory.add(conversationId, new AssistantMessage(stringBuilder.toString()));
                    }
                })
                .doOnError(err -> {
                    hasSentFinalResult.set(true);
                    sink.tryEmitError(err);
                })
                .subscribe();
    }

    private void executeToolCalls(List<AssistantMessage.ToolCall> toolCalls, List<Message> messages, AtomicBoolean hasSentFinalResult, AgentState agentState, Runnable onComplete) {
        AtomicInteger completedCount = new AtomicInteger(0);
        int totalToolCalls = toolCalls.size();

        // 保证顺序一致性
        Map<String, ToolResponseMessage.ToolResponse> responseMap = new ConcurrentHashMap<>();

        for (AssistantMessage.ToolCall tc : toolCalls) {
            Schedulers.boundedElastic().schedule(() -> {
                if (hasSentFinalResult.get()) {
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }

                String toolName = tc.name();
                String argsJson = tc.arguments();

                ToolCallback callback = findTool(toolName);
                if (callback == null) {
                    // 工具未找到时，也放入 responseMap
                    responseMap.put(tc.id(), new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, "{ \"error\": \"工具未找到：" + toolName + "\" }"));
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }

                try {
                    Object result = callback.call(argsJson);
                    String resultStr = Objects.toString(result, "");

                    // 解析搜索结果（如果是 tavily search）
                    if (agentState != null) {
                        parseSearchResult(resultStr, agentState);
                    }

                    // 将结果放入 responseMap，key 为 toolCall.id()
                    responseMap.put(tc.id(), new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, resultStr));
                } catch (Exception ex) {
                    // 工具执行失败时，也放入 responseMap
                    responseMap.put(tc.id(), new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, "{ \"error\": \"工具执行失败：" + ex.getMessage() + "\" }"));
                } finally {
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                }
            });
        }
    }

    private void completeToolCall(AtomicInteger completedCount, int total,
                                  Map<String, ToolResponseMessage.ToolResponse> responseMap,
                                  List<AssistantMessage.ToolCall> originalToolCalls,
                                  List<Message> messages,
                                  Runnable onComplete) {
        int current = completedCount.incrementAndGet();
        if (current >= total) {
            // 按原始 toolCalls 的顺序重组结果
            List<ToolResponseMessage.ToolResponse> sortedResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : originalToolCalls) {
                ToolResponseMessage.ToolResponse response = responseMap.get(tc.id());
                if (response != null) {
                    sortedResponses.add(response);
                } else {
                    // 如果某个工具调用没有响应，添加一个错误响应
                    sortedResponses.add(new ToolResponseMessage.ToolResponse(
                            tc.id(), tc.name(), "{ \"error\": \"工具响应丢失\" }"));
                }
            }

            // 一次性添加所有工具响应（按原始顺序）
            messages.add(ToolResponseMessage.builder()
                    .responses(sortedResponses)
                    .build());

            onComplete.run();
        }
    }

    private void addErrorToolResponse(List<Message> messages, AssistantMessage.ToolCall toolCall, String errMsg) {
        ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                toolCall.id(),
                toolCall.name(),
                "{ \"error\": \"" + errMsg + "\" }"
        );

        messages.add(ToolResponseMessage.builder()
                .responses(List.of(tr))
                .build());
    }

    private ToolCallback findTool(String name) {
        return tools.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析搜索结果
     * 从工具返回的 JSON 中提取搜索结果并添加到 AgentState
     */
    private void parseSearchResult(String resultJson, AgentState state) {
        try {
            JsonNode root = MAPPER.readTree(resultJson);

            // tavily 搜索结果格式: [{ "text": { "results": [...] } }]
            if (!root.isArray() || root.isEmpty()) {
                return;
            }

            JsonNode first = root.get(0);
            JsonNode textNode = first.get("text");

            if (textNode == null || textNode.isNull()) {
                return;
            }

            JsonNode textJson;
            if (textNode.isTextual()) {
                textJson = MAPPER.readTree(textNode.asText());
            } else {
                textJson = textNode;
            }

            JsonNode results = textJson.get("results");
            if (results == null || !results.isArray()) {
                return;
            }

            for (JsonNode item : results) {
                String url = getSafe(item, "url");
                String title = getSafe(item, "title");
                String content = getSafe(item, "content");

                if (url != null && !url.isBlank()) {
                    state.searchResults.add(new SearchResult(url, title, content));
                }
            }
        } catch (Exception e) {
            log.warn("解析搜索结果失败: {}", e.getMessage());
        }
    }

    /**
     * 获取节点安全值
     */
    private String getSafe(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /**
     * 带参考来源的调用
     * 返回 SimpleReactResult，包含答案和搜索结果列表
     */
    public SimpleReactResult callWithReference(String conversationId, String question) {
        return executeInternal(conversationId, question, true);
    }

    /**
     * 内部执行方法
     *
     * @param withReference 是否需要返回参考来源
     */
    private SimpleReactResult executeInternal(String conversationId, String question, boolean withReference) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        boolean useMemory = conversationId != null && chatMemory != null;

        AgentState agentState = withReference ? new AgentState() : null;

        // 合并为单个 SystemMessage（部分模型不支持多个）
        messages.add(new SystemMessage(PlanExecutePrompts.getCurrentTime() + "\n\n"
                + REACT_AGENT_SYSTEM_PROMPT + "\n\n" + systemPrompt));

        // ===== 加载历史记忆 =====
        if (useMemory) {
            List<Message> history = chatMemory.get(conversationId);
            if (history != null && !history.isEmpty()) {
                messages.addAll(history);
            }
        }

        messages.add(new UserMessage("<question>" + question + "</question>"));

        // 添加记忆
        if (useMemory) {
            chatMemory.add(conversationId, new UserMessage(question));
        }

        // 迭代轮次
        int round = 0;

        while (true) {
            round++;
            if (maxRounds > 0 && round > maxRounds) {
                log.warn("=== 达到 maxRounds（{}），强制生成最终答案 ===", maxRounds);
                messages.add(new UserMessage("""
                        你已达到最大推理轮次限制。
                        请基于当前已有的上下文信息，
                        直接给出最终答案。
                        禁止再调用任何工具。
                        如果信息不完整，请合理总结和说明。
                        """));

                String forcedAnswer = chatClient.prompt().messages(messages).call().content();
                if (useMemory) {
                    chatMemory.add(conversationId, new AssistantMessage(forcedAnswer));
                }
                return SimpleReactResult.builder()
                        .answer(forcedAnswer)
                        .searchResults(agentState != null ? agentState.searchResults : Collections.emptyList())
                        .build();
            }

            ChatClientResponse chatResponse = chatClient
                    .prompt()
                    .messages(messages)
                    .call()
                    .chatClientResponse();

            AssistantMessage.Builder builder = AssistantMessage.builder().content(chatResponse.chatResponse().getResult().getOutput().getText());

            // ===== 没有工具调用，视为最终答案 =====
            if (!chatResponse.chatResponse().hasToolCalls()) {
                String finalText = chatResponse.chatResponse().getResult().getOutput().getText();
                if (useMemory) {
                    chatMemory.add(conversationId, new AssistantMessage(finalText));
                }
                return SimpleReactResult.builder()
                        .answer(finalText)
                        .searchResults(agentState.searchResults)
                        .build();
            }

            // ===== 有工具调用：执行工具 =====
            List<AssistantMessage.ToolCall> toolCalls = chatResponse.chatResponse().getResult().getOutput().getToolCalls();
            messages.add(builder.toolCalls(toolCalls).build());

            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                String toolName = toolCall.name();
                String argsJson = toolCall.arguments();

                ToolCallback callback = findTool(toolName);
                if (callback == null) {
                    addErrorToolResponse(messages, toolCall, "工具未找到：" + toolName);
                    continue;
                }

                try {
                    Object result = callback.call(argsJson);
                    String resultStr = Objects.toString(result, "");

                    // 解析搜索结果
                    if (agentState != null) {
                        parseSearchResult(resultStr, agentState);
                    }

                    ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                            toolCall.id(), toolName, resultStr);
                    messages.add(ToolResponseMessage.builder()
                            .responses(List.of(tr))
                            .build());
                } catch (Exception ex) {
                    addErrorToolResponse(messages, toolCall, "工具执行失败：" + ex.getMessage());
                }
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private ChatModel chatModel;
        private List<ToolCallback> tools;
        private String systemPrompt = "";

        private int maxReflectionRounds;

        private int maxRounds;

        private List<Advisor> advisors;

        private ChatMemory chatMemory;

        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder tools(ToolCallback... tools) {
            this.tools = Arrays.asList(tools);
            return this;
        }

        public Builder tools(List<ToolCallback> tools) {
            this.tools = tools;
            return this;
        }

        public Builder advisors(List<Advisor> advisors) {
            this.advisors = advisors;
            return this;
        }

        public Builder advisors(Advisor... advisors) {
            this.advisors = Arrays.asList(advisors);
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder maxReflectionRounds(int maxReflectionRounds) {
            this.maxReflectionRounds = maxReflectionRounds;
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public SimpleReactAgent build() {
            if (chatModel == null) {
                throw new IllegalArgumentException("chatModel 不能为空！");
            }
            return new SimpleReactAgent(name, chatModel, tools, systemPrompt, maxRounds, chatMemory, advisors, maxReflectionRounds);
        }
    }
}
