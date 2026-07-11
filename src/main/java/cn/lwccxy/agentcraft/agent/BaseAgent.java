package cn.lwccxy.agentcraft.agent;

import cn.lwccxy.agentcraft.common.AgentResponse;
import cn.lwccxy.agentcraft.entity.AiSession;
import cn.lwccxy.agentcraft.entity.vo.UpdateAnswerRequest;
import cn.lwccxy.agentcraft.prompts.ReactAgentPrompts;
import cn.lwccxy.agentcraft.service.AgentTaskManager;
import cn.lwccxy.agentcraft.service.AiSessionService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Agent抽象基类
 */
@Slf4j
public abstract class BaseAgent {

    protected final ChatModel chatModel;
    protected final String name;
    protected ChatMemory chatMemory;
    protected AiSessionService sessionService;
    protected AgentTaskManager taskManager;
    protected String agentType;

    // 是否启用推荐问题功能
    protected boolean enableRecommendations = true;

    // 计时器
    protected long startTime;
    protected long firstResponseTime;
    protected Set<String> usedTools;
    protected Long currentSessionId;
    protected String currentConversationId;
    protected String currentQuestion;
    protected String currentRecommendations;
    /** 当前登录用户 users.id（由 Controller 从 UserContext 注入） */
    protected Long currentUserId;

    /**
     * 构造函数
     */
    public BaseAgent(String name, ChatModel chatModel, String agentType) {
        this.name = name;
        this.chatModel = chatModel;
        this.agentType = agentType;
    }

    /**
     * 子类必须实现的执行方法
     *
     * @param conversationId 会话ID
     * @param question       用户问题
     * @return 流式输出
     */
    public abstract Flux<String> execute(String conversationId, String question);

    // ===== 通用方法 =====

    /**
     * 加载历史记忆并添加到消息列表
     *
     * @param conversationId 会话ID
     * @param messages       目标消息列表
     * @param skipSystem     是否跳过系统消息
     * @param addLabel       是否添加"对话历史："标签
     */
    protected void loadChatHistory(String conversationId, List<Message> messages, boolean skipSystem, boolean addLabel) {
        if (conversationId != null && chatMemory != null) {
            List<Message> history = chatMemory.get(conversationId);
            if (history != null && !history.isEmpty()) {
                if (addLabel) {
                    messages.add(new UserMessage("对话历史："));
                }
                for (Message msg : history) {
                    if (skipSystem && msg instanceof SystemMessage) {
                        continue;
                    }
                    messages.add(msg);
                }
            }
        }
    }

    /**
     * 获取历史消息列表
     *
     * @param conversationId 会话ID
     * @return 历史消息列表
     */
    protected List<Message> getChatHistory(String conversationId) {
        if (conversationId != null && chatMemory != null) {
            return chatMemory.get(conversationId);
        }
        return null;
    }

    /**
     * 从数据库加载历史记录创建持久化ChatMemory
     *
     * @param sessionId   会话ID
     * @param maxMessages 最大消息数
     * @return ChatMemory实例
     */
    public ChatMemory createPersistentChatMemory(String sessionId, int maxMessages) {
        if (sessionService == null) {
            log.warn("sessionService is null, cannot load chat memory");
            return MessageWindowChatMemory.builder().maxMessages(maxMessages).build();
        }

        // 查询数据库中的对话历史（按 userId 隔离，避免跨用户读到历史）
        List<AiSession> history = sessionService.findRecentBySessionId(sessionId, currentUserId, maxMessages);

        // 创建 ChatMemory
        ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(maxMessages).build();

        // 将历史记录添加到 ChatMemory（按时间顺序）
        if (history != null && !history.isEmpty()) {
            // 反转历史记录顺序，确保按时间顺序添加
            for (int i = history.size() - 1; i >= 0; i--) {
                AiSession record = history.get(i);
                // 添加用户问题
                if (record.getQuestion() != null) {
                    chatMemory.add(sessionId, new UserMessage(record.getQuestion()));
                }

                // 添加AI回复
                if (record.getAnswer() != null) {
                    chatMemory.add(sessionId, new AssistantMessage(record.getAnswer()));
                }
            }
            log.debug("加载会话历史: sessionId={}, recordCount={}", sessionId, history.size());
        }

        return chatMemory;
    }

    /**
     * 创建Agent响应
     *
     * @param content 内容
     * @param type    类型
     * @return JSON格式的响应字符串
     */
    protected String createResponse(String content, String type) {
        return AgentResponse.json(type, content);
    }

    /**
     * 创建text类型响应
     *
     * @param content 内容
     * @return JSON格式的响应字符串
     */
    protected String createTextResponse(String content) {
        return AgentResponse.text(content);
    }

    /**
     * 创建thinking类型响应
     *
     * @param content 内容
     * @return JSON格式的响应字符串
     */
    protected String createThinkingResponse(String content) {
        return AgentResponse.thinking(content);
    }

    /**
     * 创建reference类型响应
     *
     * @param content 内容（JSON数组字符串，count会自动计算）
     * @return JSON格式的响应字符串
     */
    protected String createReferenceResponse(String content) {
        return AgentResponse.reference(content);
    }

    /**
     * 创建error类型响应
     *
     * @param content 内容
     * @return JSON格式的响应字符串
     */
    protected String createErrorResponse(String content) {
        return AgentResponse.error(content);
    }

    /**
     * 创建recommend类型响应
     *
     * @param content 内容（推荐问题JSON数组字符串）
     * @return JSON格式的响应字符串
     */
    protected String createRecommendResponse(String content) {
        return AgentResponse.recommend(content);
    }

    /**
     * 记录首次响应时间
     */
    protected void recordFirstResponse() {
        if (firstResponseTime == 0 && startTime > 0) {
            firstResponseTime = System.currentTimeMillis() - startTime;
            log.debug("记录首次响应时间: {}ms", firstResponseTime);
        }
    }

    /**
     * 检查并发任务
     *
     * @param conversationId 会话ID
     * @return 错误流，如果没有冲突则返回null
     */
    protected Flux<String> checkRunningTask(String conversationId) {
        if (conversationId != null && taskManager != null && taskManager.hasRunningTask(conversationId)) {
            return Flux.error(new IllegalStateException("该会话正在执行中，请稍后再试"));
        }
        return null;
    }

    /**
     * 注册任务到管理器
     *
     * @param conversationId 会话ID
     * @param sink           响应sink
     * @return 任务信息，如果注册失败返回null
     */
    protected AgentTaskManager.TaskInfo registerTask(String conversationId, Sinks.Many<String> sink) {
        if (conversationId != null && taskManager != null) {
            AgentTaskManager.TaskInfo taskInfo = taskManager.registerTask(conversationId, sink, agentType);
            if (taskInfo == null) {
                log.warn("任务注册失败: conversationId={}", conversationId);
            }
            return taskInfo;
        }
        return null;
    }

    /**
     * 初始化计时器
     */
    protected void initTimers() {
        startTime = System.currentTimeMillis();
        firstResponseTime = 0;
    }

    /**
     * 获取总响应时间
     *
     * @return 总响应时间（毫秒）
     */
    protected long getTotalResponseTime() {
        if (startTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 获取使用的工具列表字符串
     *
     * @return 逗号分隔的工具名称字符串
     */
    protected String getUsedToolsString() {
        if (usedTools == null || usedTools.isEmpty()) {
            return "";
        }
        return String.join(",", usedTools);
    }

    /**
     * 清除工具记录
     */
    protected void clearUsedTools() {
        if (usedTools != null) {
            usedTools.clear();
        }
    }

    /**
     * 记录使用的工具
     *
     * @param toolName 工具名称
     */
    protected void recordUsedTool(String toolName) {
        if (usedTools != null && toolName != null) {
            usedTools.add(toolName);
        }
    }

    /**
     * 生成推荐问题
     *
     * @param conversationId  会话ID
     * @param currentQuestion 当前问题
     * @param currentAnswer   当前答案
     * @return 推荐问题JSON字符串，失败返回null
     */
    protected String generateRecommendations(String conversationId, String currentQuestion, String currentAnswer) {
        if (!enableRecommendations) {
            return null;
        }

        try {
            List<Message> messages = new ArrayList<>();

            // 1. 添加系统提示词
            messages.add(new SystemMessage(ReactAgentPrompts.getRecommendPrompt()));

            // 2. 添加历史消息
            loadChatHistory(conversationId, messages, true, true);

            // 3. 添加当前会话的消息（最新的消息，放在最后）
            messages.add(new UserMessage("当前会话："));
            messages.add(new UserMessage(currentQuestion));
            if (currentAnswer != null) {
                messages.add(new AssistantMessage(currentAnswer));
            }

            // 4. 添加格式说明消息
            // 使用 BeanOutputConverter 进行结构化输出
            BeanOutputConverter<List<String>> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
            });

            // 添加格式说明消息
            messages.add(new UserMessage("请根据上述对话生成3个推荐问题。输出格式为：\n" + converter.getFormat()));

            // 5. 调用模型生成推荐问题
            String response = ChatClient.builder(chatModel).build()
                    .prompt()
                    .messages(messages)
                    .call()
                    .content();

            // 6. 使用 converter 转换响应
            if (response != null && !response.isEmpty()) {
                List<String> recommendations = converter.convert(response);
                if (recommendations != null && !recommendations.isEmpty()) {
                    String jsonStr = JSON.toJSONString(recommendations);
                    log.info("生成推荐问题成功: {}", jsonStr);
                    return jsonStr;
                }
            }

            log.warn("生成推荐问题失败，响应格式无效: {}", response);
            return null;
        } catch (Exception e) {
            log.error("生成推荐问题异常", e);
            return null;
        }
    }

    /**
     * 从响应中提取JSON数组
     *
     * @param response 响应字符串
     * @return JSON数组字符串，提取失败返回null
     */
    private String extractJsonArray(String response) {
        if (response == null) {
            return null;
        }

        // 查找第一个 [ 和最后一个 ]
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');

        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        return null;
    }

    // ===== 保存会话的通用方法 =====

    /**
     * 保存会话结果
     *
     * @param request 更新请求
     * @return 是否保存成功
     */
    protected boolean updateAnswer(UpdateAnswerRequest request) {
        if (sessionService != null) {
            boolean result = sessionService.updateAnswer(request);
            if (result) {
                log.debug("保存会话结果: sessionId={}, answerLength={}", request.getId(), request.getAnswer().length());
            }
            return result;
        }
        return false;
    }

    // ===== Getters and Setters =====

    public void setChatMemory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    public void setSessionService(AiSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public void setTaskManager(AgentTaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public Long getCurrentSessionId() {
        return currentSessionId;
    }

    public String getCurrentConversationId() {
        return currentConversationId;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setEnableRecommendations(boolean enableRecommendations) {
        this.enableRecommendations = enableRecommendations;
    }

    public boolean isEnableRecommendations() {
        return enableRecommendations;
    }

    public void setCurrentUserId(Long currentUserId) {
        this.currentUserId = currentUserId;
    }
}
