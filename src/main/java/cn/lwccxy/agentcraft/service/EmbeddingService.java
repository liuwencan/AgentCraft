package cn.lwccxy.agentcraft.service;

import cn.lwccxy.agentcraft.utils.DynamicPgVectorStoreFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmbeddingService {
    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private DynamicPgVectorStoreFactory pgVectorStoreFactory;

    @Autowired
    private ChatModel chatModel;


    private PgVectorStore vectorStore;

    private static final int EMBEDDING_BATCH_SIZE = 9;

    @PostConstruct
    public void init(){
        vectorStore = pgVectorStoreFactory.createPgVectorStore("vector_file_info");
    }

    /**
     * 向量化
     */
    public List<float[]> embed(List<Document> documents) {
        return documents.stream().map(document -> embeddingModel.embed(document.getText())).collect(Collectors.toList());
    }

    /**
     * 存储向量库
     */
    public void embedAndStore(List<Document> documents) {
        for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_SIZE) {
            List<Document> batches = documents.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, documents.size()));
            vectorStore.doAdd(batches);
        }
    }

    /**
     * RAG 检索 - 根据文件ID和问题检索相关文档
     *
     * @param fileId   文件ID
     * @param question 用户问题
     * @return 相关文档内容列表
     */
    public List<String> ragRetrieve(String fileId, String question) {
        log.info("RAG 检索开始: fileId={}, question={}", fileId, question);

        if (StringUtils.isBlank(fileId) || StringUtils.isBlank(question)) {
            log.warn("RAG 检索参数为空: fileId={}, question={}", fileId, question);
            return Collections.singletonList("检索参数不能为空");
        }

        try {
            Query query = Query.builder().text(question).build();

            // 1. 问题压缩重写
            ChatClient chatClient = ChatClient.builder(chatModel).build();
            CompressionQueryTransformer queryTransformer = CompressionQueryTransformer.builder()
                    .chatClientBuilder(chatClient.mutate())
                    .build();

            Query compressed = queryTransformer.transform(query);
            log.info("压缩重写后的Query: {}", compressed.text());

            // 2. 问题扩展
            QueryExpander queryExpander = MultiQueryExpander.builder()
                    .chatClientBuilder(chatClient.mutate())
                    .numberOfQueries(3)
                    .includeOriginal(true)
                    .build();

            List<Query> expandedQueries = queryExpander.expand(compressed);
            log.info("扩展后的Query：{}", expandedQueries);

            // 3. 语义向量检索 - 使用 fileid 过滤
            List<String> results = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();

            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            Filter.Expression filter = builder.eq("fileid", fileId).build();

            for (Query eq : expandedQueries) {
                List<Document> docs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(eq.text())
                                .topK(5)
                                .filterExpression(filter)
                                .build());

                for (Document doc : docs) {
                    if (seenIds.add(doc.getId())) {
                        results.add(doc.getText());
                    }
                }
            }

            log.info("RAG 检索完成: fileId={}, 返回结果数={}", fileId, results.size());
            return results;

        } catch (Exception e) {
            log.error("RAG 检索失败: fileId={}, question={}", fileId, question, e);
            return Collections.singletonList("RAG 检索失败: " + e.getMessage());
        }
    }
}
