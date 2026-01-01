package cn.lwccxy.agentcraft.agent.websearch;

import cn.lwccxy.agentcraft.agent.BaseAgent;
import cn.lwccxy.agentcraft.entity.record.AgentState;
import cn.lwccxy.agentcraft.entity.record.RoundMode;
import cn.lwccxy.agentcraft.entity.record.RoundState;
import cn.lwccxy.agentcraft.utils.ThinkTagParser;
import cn.lwccxy.agentcraft.entity.record.SearchResult;
import cn.lwccxy.agentcraft.prompts.ReactAgentPrompts;
import cn.lwccxy.agentcraft.entity.AiSession;
import cn.lwccxy.agentcraft.entity.vo.SaveQuestionRequest;
import cn.lwccxy.agentcraft.entity.vo.UpdateAnswerRequest;
import cn.lwccxy.agentcraft.service.AgentTaskManager;
import cn.lwccxy.agentcraft.service.AiSessionService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSearch React Agent
 * 基于联网搜索的智能问答Agent
 */
@Slf4j
public class WebSearchReactAgent extends BaseAgent {

    private ChatClient chatClient;
    private final List<ToolCallback> tools;
    private final String systemPrompt;
    private int maxRounds;
    private final List<Advisor> advisors;
    private final int maxReflectionRounds;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public WebSearchReactAgent(String name, ChatModel chatModel, List<ToolCallback> tools, String systemPrompt, int maxRounds,
                               ChatMemory chatMemory, List<Advisor> advisors, int maxReflectionRounds,
                               AiSessionService sessionService, AgentTaskManager taskManager) {
        super(name, chatModel, "websearch");
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.maxRounds = maxRounds;
        this.advisors = advisors;
        this.maxReflectionRounds = maxReflectionRounds;
        this.chatMemory = chatMemory;
        this.sessionService = sessionService;
        this.taskManager = taskManager;

        // 初始化工具记录集合
        this.usedTools = new HashSet<>();

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

    @Override
    public Flux<String> execute(String conversationId, String question) {
        return streamInternal(conversationId, question);
    }

    /**
     * 流式输出
     */
    public Flux<String> stream(String question) {
        return streamInternal(null, question);
    }

    /**
     * 带会话记忆的流式输出
     */
    public Flux<String> stream(String conversationId, String question) {
        return streamInternal(conversationId, question);
    }

    private Flux<String> streamInternal(String conversationId, String question) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        boolean useMemory = conversationId != null && chatMemory != null;

        // 检查是否已有任务在执行
        Flux<String> checkResult = checkRunningTask(conversationId);
        if (checkResult != null) {
            return checkResult;
        }

        // 初始化计时器
        initTimers();
        clearUsedTools();

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 注册任务到管理器
        AgentTaskManager.TaskInfo taskInfo = registerTask(conversationId, sink);
        if (taskInfo == null && conversationId != null && taskManager != null) {
            return Flux.error(new IllegalStateException("该会话正在执行中，请稍后再试"));
        }

        // ===== 加载 System Prompt（始终放在最开始）=====
        messages.add(new SystemMessage(ReactAgentPrompts.getWebSearchPrompt()));
        if (StringUtils.isNotBlank(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }

        // ===== 加载历史记忆 =====
        loadChatHistory(conversationId, messages, true, true);

        messages.add(new UserMessage("<question>" + question + "</question>"));
        currentQuestion = question;

        // 添加记忆并保存到数据库
        if (sessionService != null) {
            // 保存用户问题到数据库
            AiSession savedSession = sessionService.saveQuestion(
                    SaveQuestionRequest.builder()
                            .sessionId(conversationId)
                            .question(question)
                            .build()
            );
            currentSessionId = savedSession.getId();
        }

        // 迭代轮次
        AtomicLong roundCounter = new AtomicLong(0);
        // 是否发送最终结果标记位
        AtomicBoolean hasSentFinalResult = new AtomicBoolean(false);

        hasSentFinalResult.set(false);
        roundCounter.set(0);

        // 收集最终答案（纯文本），存储memory
        StringBuilder finalAnswerBuffer = new StringBuilder();
        // 收集思考过程
        StringBuilder thinkingBuffer = new StringBuilder();

        AgentState agentState = new AgentState();

        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId, agentState, thinkingBuffer);

        return sink.asFlux()
                .doOnNext(chunk -> {
                    recordFirstResponse();
                    // 解析 JSON，如果是 type=text，则只拼接 content；如果是 type=thinking，则拼接 thinking
                    try {
                        JSONObject json = JSON.parseObject(chunk);
                        String type = json.getString("type");
                        if ("text".equals(type)) {
                            finalAnswerBuffer.append(json.getString("content"));
                        } else if ("thinking".equals(type)) {
                            thinkingBuffer.append(json.getString("content"));
                        }
                    } catch (Exception e) {
                        // 解析失败，直接拼接
                        finalAnswerBuffer.append(chunk);
                    }
                })
                .doOnCancel(() -> {
                    hasSentFinalResult.set(true);
                    if (taskManager != null) {
                        taskManager.stopTask(conversationId);
                    }
                })
                .doFinally(signalType -> {
                    log.info("最终答案: {}", finalAnswerBuffer);
                    log.info("思考过程: {}", thinkingBuffer);

                    // 保存结果到会话
                    saveSessionResult(conversationId, finalAnswerBuffer, thinkingBuffer, agentState);

                    // 流结束时移除任务
                    if (taskManager != null) {
                        taskManager.stopTask(conversationId);
                    }
                });
    }

    /**
     * 保存会话结果
     */
    private void saveSessionResult(String conversationId, StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer, AgentState agentState) {
        if (sessionService != null && currentSessionId != null && finalAnswerBuffer.length() > 0) {
            long totalResponseTime = getTotalResponseTime();
            String toolsStr = getUsedToolsString();
            String referenceJson = "";
            if (!agentState.searchResults.isEmpty()) {
                referenceJson = createReferenceResponse(JSON.toJSONString(agentState.searchResults));
            }
            UpdateAnswerRequest request = UpdateAnswerRequest.builder()
                    .id(currentSessionId)
                    .answer(finalAnswerBuffer.toString())
                    .thinking(thinkingBuffer.toString())
                    .tools(toolsStr)
                    .reference(referenceJson)
                    .recommend(currentRecommendations)
                    .firstResponseTime(firstResponseTime)
                    .totalResponseTime(totalResponseTime)
                    .build();
            sessionService.updateAnswer(request);
            log.info("结果已保存到会话: sessionId={}", conversationId);
        }
    }

    private void scheduleRound(List<Message> messages, Sinks.Many<String> sink, AtomicLong roundCounter, AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer, boolean useMemory, String conversationId, AgentState agentState, StringBuilder thinkingBuffer) {
        // 轮次+1
        roundCounter.incrementAndGet();
        RoundState state = new RoundState();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> processChunk(chunk, sink, state))
                .doOnComplete(() -> finishRound(messages, sink, state, roundCounter, hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId, agentState, thinkingBuffer))
                .doOnError(err -> {
                    if (!hasSentFinalResult.get()) {
                        hasSentFinalResult.set(true);
                        sink.tryEmitError(err);
                    }
                })
                .subscribe();

        // 保存Disposable到任务管理器
        if (conversationId != null && taskManager != null) {
            taskManager.setDisposable(conversationId, disposable);
        }
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
                if (segment.thinking()) {
                    sink.tryEmitNext(createThinkingResponse(segment.content()));
                } else {
                    sink.tryEmitNext(createTextResponse(segment.content()));
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

        // 新的 toolcall
        state.toolCalls.add(incoming);
    }

    /**
     * 轮次结束处理工具调用
     */
    private void finishRound(List<Message> messages, Sinks.Many<String> sink, RoundState state,
                             AtomicLong roundCounter, AtomicBoolean hasSentFinalResult, StringBuilder finalAnswerBuffer,
                             boolean useMemory, String conversationId, AgentState agentState, StringBuilder thinkingBuffer) {

        // 如果整轮都没有 tool_call，才是最终答案
        if (state.getMode() != RoundMode.TOOL_CALL) {
            String referenceJson = "";
            String toolsStr = getUsedToolsString();
            String finalText = state.textBuffer.toString();

            // 输出参考链接
            if (!agentState.searchResults.isEmpty()) {
                String reference = JSON.toJSONString(agentState.searchResults);
                referenceJson = createReferenceResponse(reference);
                sink.tryEmitNext(referenceJson);
            }

            // 输出推荐问题
            if (enableRecommendations) {
                String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
                if (recommendations != null) {
                    currentRecommendations = recommendations; // 保存用于数据库存储
                    String recommendJson = createRecommendResponse(recommendations);
                    sink.tryEmitNext(recommendJson);
                }
            }

            sink.tryEmitComplete();
            hasSentFinalResult.set(true);
            return;
        }

        // TOOL_CALL
        AssistantMessage assistantMsg = AssistantMessage.builder().toolCalls(state.toolCalls).build();
        messages.add(assistantMsg);

        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
            forceFinalStream(messages, sink, hasSentFinalResult, state, conversationId, useMemory, agentState, thinkingBuffer);
            return;
        }

        executeToolCalls(sink, state.toolCalls, messages, hasSentFinalResult, state, agentState, () -> {
            if (!hasSentFinalResult.get()) {
                scheduleRound(messages, sink, roundCounter,
                        hasSentFinalResult, finalAnswerBuffer,
                        useMemory, conversationId, agentState, thinkingBuffer);
            }
        });
    }

    private void forceFinalStream(List<Message> messages, Sinks.Many<String> sink, AtomicBoolean hasSentFinalResult, RoundState state,
                                  String conversationId, boolean useMemory, AgentState agentState, StringBuilder thinkingBuffer) {
        // 创建新的消息列表，确保系统提示词在最前面
        List<Message> newMessages = new ArrayList<>();

        // 添加系统提示词
        newMessages.add(new SystemMessage(ReactAgentPrompts.getWebSearchPrompt()));
        if (StringUtils.isNotBlank(systemPrompt)) {
            newMessages.add(new SystemMessage(systemPrompt));
        }

        // 添加原有消息（跳过系统消息）
        for (Message msg : messages) {
            if (!(msg instanceof SystemMessage)) {
                newMessages.add(msg);
            }
        }

        // 添加限制提示
        newMessages.add(new UserMessage("""
                你已达到最大推理轮次限制。
                请基于当前已有的上下文信息，
                直接给出最终答案。
                禁止再调用任何工具。
                如果信息不完整，请合理总结和说明。
                """));

        // 替换原消息列表
        messages.clear();
        messages.addAll(newMessages);

        // 收集最终文本
        StringBuilder finalTextBuffer = new StringBuilder();

        Disposable disposable = chatClient.prompt()
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
                        ThinkTagParser.ParseResult parseResult = ThinkTagParser.parse(text, state.inThink);
                        state.inThink = parseResult.inThink();
                        for (ThinkTagParser.Segment segment : parseResult.segments()) {
                            if (segment.thinking()) {
                                sink.tryEmitNext(createThinkingResponse(segment.content()));
                            } else {
                                sink.tryEmitNext(createTextResponse(segment.content()));
                                finalTextBuffer.append(segment.content());
                            }
                        }
                    }
                })
                .doOnComplete(() -> {
                    String referenceJson = "";
                    String finalText = finalTextBuffer.toString();

                    // 输出参考链接
                    if (!agentState.searchResults.isEmpty()) {
                        String reference = JSON.toJSONString(agentState.searchResults);
                        referenceJson = createReferenceResponse(reference);
                        sink.tryEmitNext(referenceJson);
                    }

                    // 输出推荐问题
                    if (enableRecommendations) {
                        String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
                        if (recommendations != null) {
                            currentRecommendations = recommendations; // 保存用于数据库存储
                            String recommendJson = createRecommendResponse(recommendations);
                            sink.tryEmitNext(recommendJson);
                        }
                    }

                    hasSentFinalResult.set(true);
                    sink.tryEmitComplete();
                })
                .doOnError(err -> {
                    hasSentFinalResult.set(true);
                    sink.tryEmitError(err);
                })
                .subscribe();

        // 保存Disposable到任务管理器
        if (conversationId != null && taskManager != null) {
            taskManager.setDisposable(conversationId, disposable);
        }
    }

    private void executeToolCalls(Sinks.Many<String> sink, List<AssistantMessage.ToolCall> toolCalls, List<Message> messages, AtomicBoolean hasSentFinalResult, RoundState state, AgentState agentState, Runnable onComplete) {
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
                if (toolName.contains("search")) {
                    JSONObject args = JSON.parseObject(argsJson);
                    String query = (String) args.get("query");
                    // 发送 thinking 消息，表示正在搜索相关信息
                    String queryThink = StringUtils.isNotBlank(query) ? "🔍 正在搜索信息: " + query + "\n" : "🔍 正在搜索相关信息\n";
                    sink.tryEmitNext(createThinkingResponse(queryThink));
                }

                try {
                    Object result = callback.call(argsJson);
                    String resultStr = result.toString();

                    // 记录使用的工具
                    recordUsedTool(toolName);

                    // 解析 tavily 搜索结果
                    if (toolName.contains("tavily")) {
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

    private void parseSearchResult(String resultJson, AgentState state) {
        try {
            JsonNode root = MAPPER.readTree(resultJson);

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
            log.warn("解析 tavily 搜索结果失败: {}", e.getMessage());
        }
    }

    private String getSafe(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
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

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
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
        private AiSessionService sessionService;
        private AgentTaskManager taskManager;

        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder sessionService(AiSessionService sessionService) {
            this.sessionService = sessionService;
            return this;
        }

        public Builder taskManager(AgentTaskManager taskManager) {
            this.taskManager = taskManager;
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

        public WebSearchReactAgent build() {
            if (chatModel == null) {
                throw new IllegalArgumentException("chatModel 不能为空！");
            }
            return new WebSearchReactAgent(name, chatModel, tools, systemPrompt, maxRounds, chatMemory, advisors, maxReflectionRounds, sessionService, taskManager);
        }
    }
}
