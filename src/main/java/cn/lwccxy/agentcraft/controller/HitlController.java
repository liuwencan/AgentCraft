package cn.lwccxy.agentcraft.controller;


import cn.lwccxy.agentcraft.adviosr.PauseAdvisor;
import cn.lwccxy.agentcraft.agent.websearch.WebSearchReactAgent;
import cn.lwccxy.agentcraft.service.AgentTaskManager;
import cn.lwccxy.agentcraft.service.AiSessionService;
import cn.lwccxy.agentcraft.tool.FileOperationTool;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/hitl")
@Slf4j
public class HitlController {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private AiSessionService sessionService;

    @Autowired
    private AgentTaskManager taskManager;


    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    @Operation(summary = "智能问答", description = "接收用户查询并返回流式响应，使用联网搜索获取信息")
    public Flux<String> webSearchStream(@RequestParam(required = true) String query,
                                        @RequestParam(required = true) String conversationId) {
        log.info("收到网页搜索请求: query={}, conversationId={}", query, conversationId);

        if (query == null || query.trim().isEmpty()) {
            log.warn("查询参数为空或无效");
            return Flux.error(new IllegalArgumentException("查询参数不能为空"));
        }

        try {
            WebSearchReactAgent webSearchReactAgent = initWebSearchAgent();
            // 使用持久化记忆加载历史记录
            ChatMemory persistentMemory = webSearchReactAgent.createPersistentChatMemory(conversationId, 30);
            webSearchReactAgent.setChatMemory(persistentMemory);
            return webSearchReactAgent.stream(conversationId, query);
        } catch (Exception e) {
            log.error("处理网页搜索请求时发生错误: ", e);
            return Flux.error(e);
        }
    }


    @GetMapping(value = "/resume")
    @Operation(summary = "人工介入恢复执行")
    public String resume(@RequestParam(required = true) String query,
                                        @RequestParam(required = true) String conversationId) {
        log.info("收到网页搜索请求: query={}, conversationId={}", query, conversationId);
        return "";
    }


    /**
     * 初始化网页搜索 Agent
     */
    private WebSearchReactAgent initWebSearchAgent() {
        log.info("初始化人工介入 Agent...");

        return WebSearchReactAgent.builder()
                .name("hitl react")
                .chatModel(chatModel)
                .tools(ToolCallbacks.from(new FileOperationTool()))
                .sessionService(sessionService)
                .taskManager(taskManager)
                .advisors(new PauseAdvisor("deleteFile"))
                .maxRounds(5)
                .build();
    }
}
