package cn.lwccxy.agentcraft.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Slf4j
public class DynamicPgVectorStoreFactory {

    private final DataSource dataSource;
    private final EmbeddingModel embeddingModel;

    @Autowired
    public DynamicPgVectorStoreFactory(@Qualifier("pgVectorDataSource") DataSource dataSource, EmbeddingModel embeddingModel) {
        this.dataSource = dataSource;
        this.embeddingModel = embeddingModel;
    }

    public PgVectorStore createPgVectorStore(String tableName) {
        // 参数校验
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("向量表名称不能为空！");
        }
        String actualTableName = tableName.trim();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        boolean tableExists = tableExists(actualTableName);

        if (tableExists) {
            log.info("向量表 [{}] 已存在，开始直接加载PgVectorStore", actualTableName);
        } else {
            log.info("向量表 [{}] 不存在，将自动创建并初始化PgVectorStore", actualTableName);
        }

        PgVectorStore pgVectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1024)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .removeExistingVectorStoreTable(false)
                .vectorTableName(actualTableName)
                .maxDocumentBatchSize(100)
                .build();

        try {
            pgVectorStore.afterPropertiesSet();
            log.info("PgVectorStore加载/创建完成，表名：{}", actualTableName);
        } catch (Exception e) {
            log.error("PgVectorStore初始化失败，表名：{}", actualTableName, e);
            throw new RuntimeException("初始化PgVectorStore失败", e);
        }

        return pgVectorStore;
    }

    private boolean tableExists(String tableName) {
        try {
            String checkSql = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND LOWER(table_name) = LOWER(?)
                    );
                    """;
            Boolean exists = new JdbcTemplate(dataSource).queryForObject(checkSql, Boolean.class, tableName);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("检查向量表 [{}] 是否存在时发生异常", tableName, e);
            return false;
        }
    }
}
