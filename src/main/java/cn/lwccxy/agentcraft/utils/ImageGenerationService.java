package cn.lwccxy.agentcraft.utils;

import cn.lwccxy.agentcraft.common.ImageProvider;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ImageGenerationService {

    // Qwen API配置
    private static final String QWEN_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    // GrsAI nano-banana API配置
    @Value("${grsai.nanobanana.api-key}")
    private String grsAiApiKey;
    private static final String GRS_AI_GENERATION_URL = "https://grsai.dakka.com.cn/v1/draw/nano-banana";

    /**
     * 生成图像（默认使用qwen）
     *
     * @param prompt 提示词
     * @return 图像URL
     */
    public String generateImage(String prompt) {
        return generateImage(prompt, ImageProvider.QWEN);
    }

    /**
     * 生成图像
     *
     * @param prompt   提示词
     * @param provider 图像生成服务提供商
     * @return 图像URL
     */
    public String generateImage(String prompt, ImageProvider provider) {
        if (provider == ImageProvider.QWEN) {
            return generateWithQwen(prompt);
        } else {
            return generateWithNanoBanana(prompt);
        }
    }

    /**
     * 使用通义千问生成图像（multimodal-generation 同步接口）
     */
    private String generateWithQwen(String prompt) {
        try {
            // 构建请求参数
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "qwen-image-plus");

            // input 使用 messages 格式
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("text", prompt);

            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", new Object[]{textContent});

            Map<String, Object> input = new HashMap<>();
            input.put("messages", new Object[]{userMessage});
            requestBody.put("input", input);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("negative_prompt", "低分辨率，低画质，肢体畸形，手指畸形，画面过饱和，蜡像感，人脸无细节，过度光滑，画面具有AI感。构图混乱。文字模糊，扭曲。");
            parameters.put("prompt_extend", true);
            parameters.put("watermark", false);
            parameters.put("size", "1664*928");
            requestBody.put("parameters", parameters);

            // 创建HTTP请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(QWEN_API_URL))
                    .timeout(Duration.ofMinutes(5));

            // 添加请求头
            requestBuilder.header("Content-Type", "application/json");
            requestBuilder.header("Authorization", "Bearer " + apiKey);

            // 添加请求体
            String bodyStr = JSON.toJSONString(requestBody);
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyStr));

            HttpRequest request = requestBuilder.build();

            // 发送请求
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject jsonResponse = JSON.parseObject(response.body());
                log.info("Qwen图像生成响应: {}", jsonResponse);

                // 从响应中直接获取图片URL
                JSONObject output = jsonResponse.getJSONObject("output");
                if (output != null && output.containsKey("choices")) {
                    com.alibaba.fastjson2.JSONArray choices = output.getJSONArray("choices");
                    if (choices != null && choices.size() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject message = choice.getJSONObject("message");
                        if (message != null && message.containsKey("content")) {
                            com.alibaba.fastjson2.JSONArray contents = message.getJSONArray("content");
                            if (contents != null && contents.size() > 0) {
                                JSONObject content = contents.getJSONObject(0);
                                if (content.containsKey("image")) {
                                    String imageUrl = content.getString("image");
                                    log.info("Qwen图像生成成功，URL: {}", imageUrl);
                                    return imageUrl;
                                }
                            }
                        }
                    }
                }
            } else {
                log.error("Qwen HTTP请求失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Qwen图像生成失败", e);
        }
        return null;
    }

    /**
     * 使用nano-banana生成图像
     */
    private String generateWithNanoBanana(String prompt) {
        try {
            // 构建请求参数
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "nano-banana-pro");
            requestBody.put("prompt", prompt);
            requestBody.put("aspectRatio", "16:9");
            requestBody.put("imageSize", "1K");

            // 设置请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer " + grsAiApiKey);

            // 创建HTTP请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(GRS_AI_GENERATION_URL))
                    .timeout(Duration.ofMinutes(5));

            // 添加请求头
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }

            // 添加请求体
            String bodyStr = JSON.toJSONString(requestBody);
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyStr));

            HttpRequest request = requestBuilder.build();

            // 发送请求并接收流式响应
            HttpResponse<java.io.InputStream> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                // 处理Server-Sent Events (SSE) 格式的流响应
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();

                        // 检查是否是数据行 (data: ...)
                        if (line.startsWith("data: ")) {
                            String jsonData = line.substring(6).trim();

                            if (!jsonData.isEmpty() && !"[DONE]".equals(jsonData)) {
                                try {
                                    JSONObject jsonObject = JSON.parseObject(jsonData);

                                    // 检查是否完成
                                    if ("succeeded".equals(jsonObject.getString("status"))) {
                                        if (jsonObject.containsKey("results") &&
                                            !jsonObject.getJSONArray("results").isEmpty()) {
                                            JSONObject result = jsonObject.getJSONArray("results").getJSONObject(0);
                                            if (result.containsKey("url")) {
                                                String imageUrl = result.getString("url");
                                                log.info("nano-banana图像生成成功，URL: {}", imageUrl);
                                                return imageUrl;
                                            }
                                        }
                                    } else if ("failed".equals(jsonObject.getString("status")) ||
                                            "error".equals(jsonObject.getString("status"))) {
                                        log.error("nano-banana图像生成失败: {}", jsonObject.getString("error"));
                                        return null;
                                    }

                                    // 输出进度信息
                                    if (jsonObject.containsKey("progress")) {
                                        int progress = jsonObject.getIntValue("progress");
                                        log.info("nano-banana图像生成进度: {}%", progress);
                                    }
                                } catch (Exception e) {
                                    log.error("解析SSE数据失败: {}", jsonData, e);
                                }
                            }
                        }
                    }
                }

                log.warn("流式响应结束，但未收到成功的图像生成结果");
                return null;
            } else {
                log.error("HTTP请求失败，状态码: {}", response.statusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("nano-banana图像生成失败", e);
        }

        return null;
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
}
