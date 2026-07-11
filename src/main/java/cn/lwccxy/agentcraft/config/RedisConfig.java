package cn.lwccxy.agentcraft.config;

import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置：所有 Redis 操作统一走 Redisson。
 * <p>Sa-Token-Redis-Jackson 内部需要一个 RedisTemplate bean 来序列化 token，
 * 这里显式构建一个使用 RedissonConnectionFactory 的 RedisTemplate。</p>
 * <p>RedissonConnectionFactory 由 RedissonClientAutoConfiguration 创建（来自
 * spring.redis.redisson.config 配置或 redisson.yaml 文件）。</p>
 */
@Configuration
public class RedisConfig {

    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate(RedissonConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
