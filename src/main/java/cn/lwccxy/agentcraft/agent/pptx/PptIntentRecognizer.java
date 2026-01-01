package cn.lwccxy.agentcraft.agent.pptx;

import cn.lwccxy.agentcraft.agent.BaseAgent;
import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.record.pptx.PptIntent;
import cn.lwccxy.agentcraft.entity.record.pptx.PptInstStatus;
import cn.lwccxy.agentcraft.entity.record.pptx.PptIntentResult;
import cn.lwccxy.agentcraft.prompts.PptBuilderPrompts;
import cn.lwccxy.agentcraft.service.AiPptInstService;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.StringUtils;

/**
 * PPT意图识别器
 */
@Slf4j
public class PptIntentRecognizer {

    private final ChatClient chatClient;
    private final AiPptInstService pptInstService;

    public PptIntentRecognizer(ChatClient chatClient, AiPptInstService pptInstService) {
        this.chatClient = chatClient;
        this.pptInstService = pptInstService;
    }

    /**
     * 意图识别
     *
     * @param conversationId 会话ID
     * @param query          用户查询
     * @return 意图识别结果
     */
    public PptIntentResult recognize(String conversationId, String query) {
        // 获取最新的PPT实例（不限制状态）
        AiPptInst latestInst = pptInstService.getLatestInst(conversationId);

        // 如果没有PPT实例，默认为CREATE_PPT意图
        if (latestInst == null) {
            log.info("会话中无PPT实例，默认新建");
            return new PptIntentResult(PptIntent.CREATE_PPT, "会话中无PPT实例，默认新建");
        }

        PptInstStatus status = latestInst.getStatusEnum();
        String errorMsg = latestInst.getErrorMsg();

        // 检查是否需要断点重连
        if (needsResume(status, errorMsg, query)) {
            log.info("检测到断点重连需求: status={}, hasError={}", status, StringUtils.hasText(errorMsg));
            return new PptIntentResult(PptIntent.RESUME_PPT,
                    "检测到上次执行未完成，从状态 " + status + " 继续执行");
        }

        // 如果是SUCCESS状态，调用LLM进行意图识别（CREATE_PPT 或 MODIFY_PPT）
        if (status == PptInstStatus.SUCCESS) {
            return recognizeWithLLM(query);
        }

        // 对于其他中间状态（非失败），也默认为CREATE_PPT（新建）
        log.info("状态为 {}，默认新建", status);
        return new PptIntentResult(PptIntent.CREATE_PPT, "状态为 " + status + "，默认新建");
    }

    /**
     * 判断是否需要断点重连
     */
    private boolean needsResume(PptInstStatus status, String errorMsg, String query) {
        // 如果有错误信息，说明上次执行失败，需要重连
        if (StringUtils.hasText(errorMsg)) {
            return true;
        }

        // 检查用户是否明确表示要继续
        String lowerQuery = query.toLowerCase();
        String[] resumeKeywords = {"继续", "重试", "resume", "retry", "继续执行", "继续生成"};
        for (String keyword : resumeKeywords) {
            if (lowerQuery.contains(keyword)) {
                return true;
            }
        }

        // 对于中间状态（非SUCCESS、非INIT），如果用户没有明确要求新建，则继续
        if (status != PptInstStatus.SUCCESS && status != PptInstStatus.INIT) {
            // 检查用户是否明确要求新建
            String[] newKeywords = {"新建", "重新", "重新生成", "new", "create new"};
            for (String keyword : newKeywords) {
                if (lowerQuery.contains(keyword)) {
                    return false; // 用户明确要新建
                }
            }
            return true; // 默认继续
        }

        return false;
    }

    /**
     * 使用LLM进行意图识别
     */
    private PptIntentResult recognizeWithLLM(String query) {
        String prompt = PptBuilderPrompts.INTENT_RECOGNITION_PROMPT;
        BeanOutputConverter<PptIntentResult> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {});

        try {
            String response = chatClient.prompt()
                    .messages(new SystemMessage(prompt), new UserMessage("<question>" + query + "</question>"))
                    .call()
                    .content();

            log.info("LLM意图识别响应: {}", response);
            PptIntentResult result = converter.convert(response);
            return result;
        } catch (Exception e) {
            log.error("解析意图识别结果失败", e);
            return new PptIntentResult(PptIntent.CREATE_PPT, "意图识别失败，默认新建");
        }
    }
}
