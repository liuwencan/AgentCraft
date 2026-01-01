package cn.lwccxy.agentcraft.agent.skills;

import cn.lwccxy.agentcraft.agent.BaseAgent;
import cn.lwccxy.agentcraft.context.ContextCompactor;
import cn.lwccxy.agentcraft.context.ContextPolicy;
import cn.lwccxy.agentcraft.entity.AiSession;
import cn.lwccxy.agentcraft.entity.event.AgentStreamEvent;
import cn.lwccxy.agentcraft.entity.event.ToolRecord;
import cn.lwccxy.agentcraft.entity.record.RoundMode;
import cn.lwccxy.agentcraft.entity.record.RoundState;
import cn.lwccxy.agentcraft.entity.vo.SaveQuestionRequest;
import cn.lwccxy.agentcraft.entity.vo.UpdateAnswerRequest;
import cn.lwccxy.agentcraft.prompts.ReactAgentPrompts;
import cn.lwccxy.agentcraft.service.AgentTaskManager;
import cn.lwccxy.agentcraft.service.AiSessionService;
import cn.lwccxy.agentcraft.utils.ThinkTagParser;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Skills React Agent
 * 通用型智能体，集成 Skills、搜索、文件等多种能力，工具按需挂载，LLM 自动判断使用哪个工具/Skill。
 * 使用事件流模型输出（Thinking/Text/ToolStart/ToolEnd/Error/Complete）。
 *
 * @author rel
 */
@Slf4j
public class SkillsReactAgent extends BaseAgent {

    private static final long RETRY_INTERVAL_MS = 10000;

    private ChatClient chatClient;
    private final List<ToolCallback> tools;
    private final String systemPrompt;
    private int maxRounds;
    private int maxRetries;
    private String currentFileId;
    private final ContextCompactor contextCompactor;

    private final List<ToolRecord> toolRecords = Collections.synchronizedList(new ArrayList<>());

    public SkillsReactAgent(String name, ChatModel chatModel, List<ToolCallback> tools,
                            String systemPrompt, int maxRounds, int maxRetries, ChatMemory chatMemory,
                            AiSessionService sessionService, AgentTaskManager taskManager,
                            ContextPolicy contextPolicy) {
        super(name, chatModel, "skills");
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.maxRounds = maxRounds;
        this.maxRetries = maxRetries;
        this.chatMemory = chatMemory;
        this.sessionService = sessionService;
        this.taskManager = taskManager;
        this.usedTools = new HashSet<>();

        initChatClient();

        // 初始化上下文压缩器（不配置 contextPolicy 时不启用）
        this.contextCompactor = contextPolicy != null
                ? new ContextCompactor(contextPolicy, chatModel)
                : null;

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
            this.chatClient = builder.defaultOptions(toolOptions).defaultToolCallbacks(tools).build();
        } catch (Exception e) {
            throw new RuntimeException("ChatClient 初始化失败：" + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> execute(String conversationId, String question) {
        return streamInternal(conversationId, question, null);
    }

    /**
     * 带文件ID的流式输出
     */
    public Flux<String> stream(String conversationId, String question, String fileId) {
        return streamInternal(conversationId, question, fileId);
    }

    /**
     * 带会话记忆的流式输出
     */
    public Flux<String> stream(String conversationId, String question) {
        return streamInternal(conversationId, question, null);
    }

    private Flux<String> streamInternal(String conversationId, String question, String fileId) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());

        // 检查是否已有任务在执行
        Flux<String> checkResult = checkRunningTask(conversationId);
        if (checkResult != null) {
            return checkResult;
        }

        // 初始化计时器
        initTimers();
        clearUsedTools();
        toolRecords.clear();

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 注册任务到管理器
        AgentTaskManager.TaskInfo taskInfo = registerTask(conversationId, sink);
        if (taskInfo == null && conversationId != null && taskManager != null) {
            return Flux.error(new IllegalStateException("该会话正在执行中，请稍后再试"));
        }

        // 保存当前 fileId
        if (fileId != null) {
            this.currentFileId = fileId;
        }

        // ===== 加载 System Prompt =====
        String fullSystemPrompt = ReactAgentPrompts.getSkillsPrompt();
        if (StringUtils.isNotBlank(systemPrompt)) {
            fullSystemPrompt = fullSystemPrompt + "\n" + systemPrompt;
        }
        messages.add(new SystemMessage(fullSystemPrompt));

        // ===== 加载历史记忆 =====
        loadChatHistory(conversationId, messages, true, true);

        // ===== 用户消息 =====
        messages.add(new UserMessage("<question>" + question + "</question>"));
        if (this.currentFileId != null) {
            messages.add(new UserMessage("<fileid>" + this.currentFileId + "</fileid>"));
        }
        currentQuestion = question;

        // 保存用户问题到数据库
        if (sessionService != null) {
            AiSession savedSession = sessionService.saveQuestion(
                    SaveQuestionRequest.builder()
                            .sessionId(conversationId)
                            .question(question)
                            .fileid(this.currentFileId)
                            .build()
            );
            currentSessionId = savedSession.getId();
        }

        // 迭代轮次
        AtomicLong roundCounter = new AtomicLong(0);
        AtomicBoolean hasSentFinalResult = new AtomicBoolean(false);

        // 收集最终答案
        StringBuilder finalAnswerBuffer = new StringBuilder();
        // 收集思考过程
        StringBuilder thinkingBuffer = new StringBuilder();

        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, conversationId,
                finalAnswerBuffer, thinkingBuffer);

        return sink.asFlux()
                .doOnNext(chunk -> {
                    recordFirstResponse();
                    // 解析 JSON，分离收集 text 和 thinking
                    try {
                        var json = JSON.parseObject(chunk);
                        String type = json.getString("type");
                        if ("text".equals(type)) {
                            finalAnswerBuffer.append(json.getString("content"));
                        } else if ("thinking".equals(type)) {
                            thinkingBuffer.append(json.getString("content"));
                        }
                    } catch (Exception e) {
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
                    saveSessionResult(conversationId, finalAnswerBuffer, thinkingBuffer);

                    // 流结束时移除任务
                    if (taskManager != null) {
                        taskManager.stopTask(conversationId);
                    }
                });
    }

    /**
     * 保存会话结果
     */
    private void saveSessionResult(String conversationId, StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer) {
        if (sessionService != null && currentSessionId != null && finalAnswerBuffer.length() > 0) {
            long totalResponseTime = getTotalResponseTime();
            String toolsStr = getUsedToolsString();
            UpdateAnswerRequest request = UpdateAnswerRequest.builder()
                    .id(currentSessionId)
                    .answer(finalAnswerBuffer.toString())
                    .thinking(thinkingBuffer.toString())
                    .tools(toolsStr)
                    .recommend(currentRecommendations)
                    .firstResponseTime(firstResponseTime)
                    .totalResponseTime(totalResponseTime)
                    .build();
            sessionService.updateAnswer(request);
            log.info("结果已保存到会话: sessionId={}", conversationId);
        }
    }

    private void scheduleRound(List<Message> messages, Sinks.Many<String> sink,
                               AtomicLong roundCounter, AtomicBoolean hasSentFinalResult,
                               String conversationId,
                               StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer) {
        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, conversationId,
                finalAnswerBuffer, thinkingBuffer, 0);
    }

    private void scheduleRound(List<Message> messages, Sinks.Many<String> sink,
                               AtomicLong roundCounter, AtomicBoolean hasSentFinalResult,
                               String conversationId,
                               StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer,
                               int retryAttempt) {
        long round = roundCounter.incrementAndGet();
        log.info("=== Round {} 开始 === 消息数: {} retryAttempt: {}", round, messages.size(), retryAttempt);

        // 上下文压缩（每轮 LLM 调用前执行）
        if (contextCompactor != null) {
            contextCompactor.compact(messages, currentQuestion);
            log.info("=== Round {} 压缩后消息数: {} ===", round, messages.size());
        }

        RoundState state = new RoundState();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> processChunk(chunk, sink, state))
                .doOnComplete(() -> finishRound(messages, sink, state, roundCounter, hasSentFinalResult,
                        conversationId, finalAnswerBuffer, thinkingBuffer))
                .onErrorResume(err -> {
                    if (retryAttempt < maxRetries) {
                        log.warn("LLM stream error (attempt {}/{}), retrying in {}ms: {}",
                                retryAttempt + 1, maxRetries, RETRY_INTERVAL_MS, err.getMessage());
                        sink.tryEmitNext(new AgentStreamEvent.Error("LLM_CALL_FAILED",
                                "LLM 调用失败，正在重试 (" + (retryAttempt + 1) + "/" + maxRetries + ")",
                                err.getMessage()).toJSON());
                        Schedulers.boundedElastic().schedule(
                                () -> scheduleRound(messages, sink, roundCounter, hasSentFinalResult,
                                        conversationId, finalAnswerBuffer, thinkingBuffer, retryAttempt + 1),
                                RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    } else {
                        log.error("LLM stream error, retries exhausted ({})", maxRetries, err);
                        sink.tryEmitNext(new AgentStreamEvent.Error("LLM_CALL_FAILED",
                                "LLM 调用失败（已重试 " + maxRetries + " 次）",
                                err.getMessage()).toJSON());
                        sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
                        hasSentFinalResult.set(true);
                        sink.tryEmitComplete();
                    }
                    return Flux.empty();
                })
                .subscribe();

        // 保存Disposable到任务管理器
        if (conversationId != null && taskManager != null) {
            taskManager.setDisposable(conversationId, disposable);
        }
    }

    /**
     * 处理流式 chunk，使用 ThinkTagParser 拆分思考内容和正常文本
     */
    private void processChunk(ChatResponse chunk, Sinks.Many<String> sink, RoundState state) {
        if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return;
        }

        Generation gen = chunk.getResult();
        String text = gen.getOutput().getText();
        List<AssistantMessage.ToolCall> tc = gen.getOutput().getToolCalls();

        // 一旦发现 tool_call，立即进入 TOOL_CALL 模式
        if (tc != null && !tc.isEmpty()) {
            state.mode = RoundMode.TOOL_CALL;
            for (AssistantMessage.ToolCall incoming : tc) {
                log.debug("Raw tool_call chunk: id={}, name={}, args={}", incoming.id(), incoming.name(), incoming.arguments());
                mergeToolCall(state, incoming);
            }
            return;
        }

        // 使用 ThinkTagParser 拆分思考内容和正常文本
        if (text != null && !text.isEmpty()) {
            ThinkTagParser.ParseResult parseResult = ThinkTagParser.parse(text, state.inThink);
            state.inThink = parseResult.inThink();

            for (ThinkTagParser.Segment segment : parseResult.segments()) {
                if (segment.thinking()) {
                    sink.tryEmitNext(new AgentStreamEvent.Thinking(segment.content()).toJSON());
                } else {
                    sink.tryEmitNext(new AgentStreamEvent.Text(segment.content()).toJSON());
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
                        new AssistantMessage.ToolCall(existing.id(), "function", existing.name(), mergedArgs));
                return;
            }
        }
        state.toolCalls.add(incoming);
    }

    /**
     * 轮次结束处理工具调用
     */
    private void finishRound(List<Message> messages, Sinks.Many<String> sink, RoundState state,
                             AtomicLong roundCounter, AtomicBoolean hasSentFinalResult,
                             String conversationId,
                             StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer) {

        // 如果整轮都没有 tool_call，才是最终答案
        if (state.mode != RoundMode.TOOL_CALL) {
            sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
            sink.tryEmitComplete();
            hasSentFinalResult.set(true);
            return;
        }

        // TOOL_CALL
        for (AssistantMessage.ToolCall tc : state.toolCalls) {
            log.info("ToolCall merged result: name={}, args={}", tc.name(), tc.arguments());
        }
        AssistantMessage assistantMsg = AssistantMessage.builder().toolCalls(state.toolCalls).build();
        messages.add(assistantMsg);

        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
            forceFinalStream(messages, sink, hasSentFinalResult, conversationId);
            return;
        }

        executeToolCalls(sink, state.toolCalls, messages, hasSentFinalResult, () -> {
            if (!hasSentFinalResult.get()) {
                scheduleRound(messages, sink, roundCounter, hasSentFinalResult,
                        conversationId, finalAnswerBuffer, thinkingBuffer);
            }
        });
    }

    private void forceFinalStream(List<Message> messages, Sinks.Many<String> sink,
                                  AtomicBoolean hasSentFinalResult, String conversationId) {
        forceFinalStream(messages, sink, hasSentFinalResult, conversationId, 0);
    }

    private void forceFinalStream(List<Message> messages, Sinks.Many<String> sink,
                                  AtomicBoolean hasSentFinalResult, String conversationId,
                                  int retryAttempt) {
        List<Message> newMessages = new ArrayList<>();

        String forceSystemPrompt = ReactAgentPrompts.getSkillsPrompt();
        if (StringUtils.isNotBlank(systemPrompt)) {
            forceSystemPrompt = forceSystemPrompt + "\n" + systemPrompt;
        }
        newMessages.add(new SystemMessage(forceSystemPrompt));

        for (Message msg : messages) {
            if (!(msg instanceof SystemMessage)) {
                newMessages.add(msg);
            }
        }

        newMessages.add(new UserMessage("""
                你已达到最大推理轮次限制。
                请基于当前已有的上下文信息，直接给出最终答案。
                禁止再调用任何工具。
                如果信息不完整，请合理总结和说明。
                """));

        messages.clear();
        messages.addAll(newMessages);

        RoundState state = new RoundState();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> processChunk(chunk, sink, state))
                .doOnComplete(() -> {
                    sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
                    hasSentFinalResult.set(true);
                    sink.tryEmitComplete();
                })
                .onErrorResume(err -> {
                    if (retryAttempt < maxRetries) {
                        log.warn("forceFinal stream error (attempt {}/{}), retrying in {}ms: {}",
                                retryAttempt + 1, maxRetries, RETRY_INTERVAL_MS, err.getMessage());
                        sink.tryEmitNext(new AgentStreamEvent.Error("LLM_CALL_FAILED",
                                "LLM 调用失败，正在重试 (" + (retryAttempt + 1) + "/" + maxRetries + ")",
                                err.getMessage()).toJSON());
                        Schedulers.boundedElastic().schedule(
                                () -> forceFinalStream(messages, sink, hasSentFinalResult, conversationId, retryAttempt + 1),
                                RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    } else {
                        log.error("forceFinal stream error, retries exhausted ({})", maxRetries, err);
                        sink.tryEmitNext(new AgentStreamEvent.Error("LLM_CALL_FAILED",
                                "LLM 调用失败（已重试 " + maxRetries + " 次）",
                                err.getMessage()).toJSON());
                        sink.tryEmitNext(new AgentStreamEvent.Complete().toJSON());
                        hasSentFinalResult.set(true);
                        sink.tryEmitComplete();
                    }
                    return Flux.empty();
                })
                .subscribe();

        if (conversationId != null && taskManager != null) {
            taskManager.setDisposable(conversationId, disposable);
        }
    }

    private void executeToolCalls(Sinks.Many<String> sink, List<AssistantMessage.ToolCall> toolCalls,
                                  List<Message> messages, AtomicBoolean hasSentFinalResult, Runnable onComplete) {
        AtomicInteger completedCount = new AtomicInteger(0);
        int totalToolCalls = toolCalls.size();

        Map<String, ToolResponseMessage.ToolResponse> responseMap = new ConcurrentHashMap<>();

        for (AssistantMessage.ToolCall tc : toolCalls) {
            Schedulers.boundedElastic().schedule(() -> {
                if (hasSentFinalResult.get()) {
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }

                String toolName = tc.name();
                String argsJson = tc.arguments();

                // 发送 ToolStart 事件
                log.info(">>> ToolStart: {} | args: {}", toolName, argsJson);
                sink.tryEmitNext(new AgentStreamEvent.ToolStart(toolName, tc.id(), argsJson).toJSON());

                ToolCallback callback = findTool(toolName);
                if (callback == null) {
                    String errorMsg = "工具未找到：" + toolName;
                    log.warn("<<< ToolEnd (NOT_FOUND): {}", toolName);
                    sink.tryEmitNext(new AgentStreamEvent.ToolEnd(toolName, tc.id(), errorMsg).toJSON());
                    responseMap.put(tc.id(), new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, errorMsg));
                    completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    return;
                }

                try {
                    Object result = callback.call(argsJson);
                    String resultStr = Objects.toString(result, "");

                    // 记录使用的工具
                    recordUsedTool(toolName);

                    // 记录工具执行结果
                    toolRecords.add(new ToolRecord(toolName, tc.id(), argsJson, resultStr));

                    // 发送 ToolEnd 事件
                    log.info("<<< ToolEnd: {} | resultLen: {} | result: {}", toolName, resultStr.length(), resultStr);
                    sink.tryEmitNext(new AgentStreamEvent.ToolEnd(toolName, tc.id(), resultStr).toJSON());

                    responseMap.put(tc.id(), new ToolResponseMessage.ToolResponse(tc.id(), toolName, resultStr));
                } catch (Exception ex) {
                    String errorMsg = "工具执行失败：" + ex.getMessage();
                    log.error("<<< ToolEnd (ERROR): {} | {}", toolName, ex.getMessage());
                    sink.tryEmitNext(new AgentStreamEvent.ToolEnd(toolName, tc.id(), errorMsg).toJSON());
                    responseMap.put(tc.id(), new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, errorMsg));
                } finally {
                    if (!hasSentFinalResult.get()) {
                        completeToolCall(completedCount, totalToolCalls, responseMap, toolCalls, messages, onComplete);
                    }
                }
            });
        }
    }

    private void completeToolCall(AtomicInteger completedCount, int total,
                                  Map<String, ToolResponseMessage.ToolResponse> responseMap,
                                  List<AssistantMessage.ToolCall> originalToolCalls,
                                  List<Message> messages, Runnable onComplete) {
        int current = completedCount.incrementAndGet();
        if (current >= total) {
            List<ToolResponseMessage.ToolResponse> sortedResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : originalToolCalls) {
                ToolResponseMessage.ToolResponse response = responseMap.get(tc.id());
                if (response != null) {
                    sortedResponses.add(response);
                } else {
                    sortedResponses.add(new ToolResponseMessage.ToolResponse(
                            tc.id(), tc.name(), "{ \"error\": \"工具响应丢失\" }"));
                }
            }

            messages.add(ToolResponseMessage.builder().responses(sortedResponses).build());
            onComplete.run();
        }
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

    public void setCurrentFileId(String fileId) {
        this.currentFileId = fileId;
    }


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private ChatModel chatModel;
        private List<ToolCallback> tools;
        private String systemPrompt = "";
        private int maxRounds;
        private int maxRetries = 3;
        private ChatMemory chatMemory;
        private AiSessionService sessionService;
        private AgentTaskManager taskManager;
        private ContextPolicy contextPolicy;

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

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

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

        public Builder contextPolicy(ContextPolicy contextPolicy) {
            this.contextPolicy = contextPolicy;
            return this;
        }

        public SkillsReactAgent build() {
            if (chatModel == null) {
                throw new IllegalArgumentException("chatModel 不能为空！");
            }
            if (tools == null || tools.isEmpty()) {
                throw new IllegalArgumentException("tools 不能为空！");
            }
            return new SkillsReactAgent(name, chatModel, tools, systemPrompt, maxRounds, maxRetries,
                    chatMemory, sessionService, taskManager, contextPolicy);
        }
    }
}
