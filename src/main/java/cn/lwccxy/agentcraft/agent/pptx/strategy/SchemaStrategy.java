package cn.lwccxy.agentcraft.agent.pptx.strategy;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.record.pptx.AiPptTemplate;
import cn.lwccxy.agentcraft.entity.record.pptx.FieldData;
import cn.lwccxy.agentcraft.entity.record.pptx.PptInstStatus;
import cn.lwccxy.agentcraft.entity.record.pptx.PptSchema;
import cn.lwccxy.agentcraft.entity.record.pptx.Slide;
import cn.lwccxy.agentcraft.prompts.PptBuilderPrompts;
import cn.lwccxy.agentcraft.utils.ThinkTagParser;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Schema生成策略
 */
@Slf4j
public class SchemaStrategy implements PptStateStrategy {

    private static final PptInstStatus TARGET_STATUS = PptInstStatus.RENDER;

    @Override
    public void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                        StringBuilder thinkingBuffer, PptStateStrategyContext context) {
        sink.tryEmitNext(context.createThinkingResponse("正在设计PPT详细内容...\n"));

        String templateCode = inst.getTemplateCode();
        AiPptTemplate template = context.getPptTemplateService().getByCode(templateCode);
        String templateSchema = template.getTemplateSchema();
        String outline = inst.getOutline();

        String prompt = PptBuilderPrompts.getSchemaGenerationPrompt(templateSchema, outline);

        BeanOutputConverter<PptSchema> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });

        Disposable disposable = Mono.fromCallable(() -> {
                    String json = ThinkTagParser.stripThinkTags(
                            context.getChatModel().call(new Prompt(prompt)).getResult().getOutput().getText());
                    PptSchema pptSchema = converter.convert(json);
                    String pptSchemaJson = JSON.toJSONString(pptSchema);

                    context.getPptInstService().updatePptSchema(inst.getId(), pptSchemaJson, TARGET_STATUS);

                    // 处理图片生成
                    processImageGeneration(pptSchema, sink, inst.getConversationId(), context);

                    // 更新包含图片URL的schema
                    context.getPptInstService().updatePptSchema(inst.getId(), JSON.toJSONString(pptSchema), TARGET_STATUS);
                    context.continueStateMachine(inst, sink, query, thinkingBuffer);
                    return null;
                })
                .doOnError(err -> {
                    log.error("Schema生成异常", err);
                    // 失败时不回退状态，只更新错误信息，转到 FAILED
                    context.getPptInstService().updateError(inst.getId(),
                            "Schema生成失败: " + err.getMessage(), PptInstStatus.SCHEMA);
                    // 转到 FAILED 策略
                    PptStateStrategyFactory.getInstance().executeFailedState(inst, sink, query, thinkingBuffer, context);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        // 保存 disposable 到任务管理器，用于停止任务
        context.setDisposable(inst.getConversationId(), disposable);
    }

    /**
     * 执行 Schema 策略，支持修改模式
     *
     * @param inst PPT 实例
     * @param sink 输出 sink
     * @param query 用户查询
     * @param thinkingBuffer 思考缓冲
     * @param context 策略上下文
     * @param modifyPrompt 修改提示词，如果为 null 表示正常流程
     */
    public void executeWithModifyPrompt(AiPptInst inst, Sinks.Many<String> sink, String query,
                                        StringBuilder thinkingBuffer, PptStateStrategyContext context,
                                        String modifyPrompt) {
        sink.tryEmitNext(context.createThinkingResponse("正在重新生成PPT详细内容...\n"));

        BeanOutputConverter<PptSchema> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });

        Disposable disposable = Mono.fromCallable(() -> {
                    String json = ThinkTagParser.stripThinkTags(
                            context.getChatModel().call(new Prompt(modifyPrompt)).getResult().getOutput().getText());
                    PptSchema pptSchema = converter.convert(json);
                    String pptSchemaJson = JSON.toJSONString(pptSchema);

                    context.getPptInstService().updatePptSchema(inst.getId(), pptSchemaJson, TARGET_STATUS);

                    // 处理图片生成
                    processImageGeneration(pptSchema, sink, inst.getConversationId(), context);

                    // 更新包含图片URL的schema
                    context.getPptInstService().updatePptSchema(inst.getId(), JSON.toJSONString(pptSchema), TARGET_STATUS);
                    context.continueStateMachine(inst, sink, query, thinkingBuffer);
                    return null;
                })
                .doOnError(err -> {
                    log.error("Schema生成异常", err);
                    // 失败时不回退状态，只更新错误信息，转到 FAILED
                    context.getPptInstService().updateError(inst.getId(),
                            "Schema生成失败: " + err.getMessage(), PptInstStatus.SCHEMA);
                    // 转到 FAILED 策略
                    PptStateStrategyFactory.getInstance().executeFailedState(inst, sink, query, thinkingBuffer, context);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        // 保存 disposable 到任务管理器，用于停止任务
        context.setDisposable(inst.getConversationId(), disposable);
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }

    /**
     * 处理图片生成
     */
    private void processImageGeneration(PptSchema pptSchema, Sinks.Many<String> sink, String conversationId,
                                        PptStateStrategyContext context) {
        if (pptSchema.getSlides() == null) {
            return;
        }

        // 首先收集所有需要生成图片的字段
        List<ImageGenerationTask> tasks = new ArrayList<>();
        for (Slide slide : pptSchema.getSlides()) {
            if (slide.getData() == null) {
                continue;
            }

            for (Map.Entry<String, FieldData> entry : slide.getData().entrySet()) {
                String key = entry.getKey();
                FieldData fieldData = entry.getValue();
                if (fieldData == null) {
                    continue;
                }

                String type = fieldData.getType();
                // 只处理image和background类型
                if (!"image".equalsIgnoreCase(type) && !"background".equalsIgnoreCase(type)) {
                    continue;
                }

                // 如果url已经有值，跳过
                if (fieldData.getUrl() != null && !fieldData.getUrl().isEmpty()) {
                    continue;
                }

                // url为空，需要用content作为提示词生成图片
                String prompt = fieldData.getContent();
                if (prompt == null || prompt.isEmpty()) {
                    continue;
                }

                tasks.add(new ImageGenerationTask(key, fieldData, prompt, slide));
            }
        }

        if (tasks.isEmpty()) {
            return;
        }

        int total = tasks.size();
        sink.tryEmitNext(context.createThinkingResponse("✅PPT内容设计完成，开始生成图片素材\n"));

        sink.tryEmitNext(context.createThinkingResponse("共需生成 " + total + " 张图片，开始生成...\n"));

        // 逐个生成图片
        for (int i = 0; i < tasks.size(); i++) {
            ImageGenerationTask task = tasks.get(i);
            int current = i + 1;

            sink.tryEmitNext(context.createThinkingResponse("正在生成图片 (" + current + "/" + total + ")... \n"));

            try {
                // 调用图片生成服务
                String originalImageUrl = context.getImageGenerationService().generateImage(task.prompt);

                // 下载图片并上传到MinIO
                byte[] imageBytes = downloadImageFromUrl(originalImageUrl);

                if (imageBytes != null && imageBytes.length > 0) {
                    // 上传到MinIO
                    String objectName = "ppt/" + conversationId + "/images/" + System.currentTimeMillis() + "_" + (i + 1) + ".png";
                    String minioUrl = context.getMinioService().uploadFile(objectName, imageBytes, "image/png");

                    // 更新schema中的url为MinIO地址
                    task.fieldData.setUrl(minioUrl);

                    sink.tryEmitNext(context.createThinkingResponse("✅ 图片生成完成 (" + current + "/" + total + ")\n"));
                    log.info("图片已上传到MinIO: {} -> {}", task.key, minioUrl);
                } else {
                    throw new RuntimeException("图片下载失败");
                }

            } catch (Exception e) {
                log.error("图片生成或上传失败: {}", task.prompt, e);
                sink.tryEmitNext(context.createThinkingResponse("⚠ 图片生成失败 (" + current + "/" + total + "): \n" + task.key));
                // 使用空字符串
                task.fieldData.setUrl("");
            }
        }
        sink.tryEmitNext(context.createThinkingResponse("✅ 所有图片生成完成\n"));
        sink.tryEmitNext(context.createThinkingResponse("✅素材准备就绪，开始渲染PPT\n"));
    }

    /**
     * 从URL下载图片
     */
    private byte[] downloadImageFromUrl(String imageUrl) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("下载图片失败，状态码: " + response.statusCode());
        }
    }

    /**
     * 图片生成任务
     */
    private static class ImageGenerationTask {
        String key;
        FieldData fieldData;
        String prompt;
        Slide slide;

        ImageGenerationTask(String key, FieldData fieldData, String prompt, Slide slide) {
            this.key = key;
            this.fieldData = fieldData;
            this.prompt = prompt;
            this.slide = slide;
        }
    }
}
