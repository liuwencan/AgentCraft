package cn.lwccxy.agentcraft.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class VectorStoreConfig {

    /**
     * 全局唯一连接池
     */
    @Bean(name = "pgVectorDataSource")
    public DataSource pgVectorDataSource(
            @Value("${embeddings.store.host}") String host,
            @Value("${embeddings.store.port}") String port,
            @Value("${embeddings.store.database}") String database,
            @Value("${embeddings.store.user}") String user,
            @Value("${embeddings.store.password}") String password
    ) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");

        ds.setMaximumPoolSize(50);
        ds.setMinimumIdle(5);
        ds.setPoolName("PgVectorPool");

        return ds;
    }
}
