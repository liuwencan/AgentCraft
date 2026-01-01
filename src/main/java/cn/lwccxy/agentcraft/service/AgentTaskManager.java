package cn.lwccxy.agentcraft.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Agent任务管理器
 * 用于管理流式输出的停止和中断
 */
@Slf4j
@Component
public class AgentTaskManager implements InitializingBean, DisposableBean {

    private static final String TASK_KEY_PREFIX = "agent:task:";
    private static final String STOP_TOPIC_NAME = "agent:stop";
    private static final long TASK_TTL_MINUTES = 30;
    private static final long TTL_REFRESH_INTERVAL_MINUTES = 5;

    /**
     * 当前实例的唯一标识
     */
    private final String instanceId;

    /**
     * Redisson 客户端
     */
    private final RedissonClient redissonClient;

    /**
     * 停止消息的发布订阅主题
     */
    private final RTopic stopTopic;

    /**
     * 本地任务映射（conversationId -> TaskInfo）
     * 仅包含当前实例上运行的任务
     */
    private final Map<String, TaskInfo> taskMap = new ConcurrentHashMap<>();

    /**
     * TTL 刷新定时器
     */
    private final ScheduledExecutorService ttlRefreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "agent-ttl-refresh");
        t.setDaemon(true);
        return t;
    });

    /**
     * 发布订阅监听器ID（用于销毁时移除）
     */
    private int listenerId;

    public AgentTaskManager(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        this.stopTopic = redissonClient.getTopic(STOP_TOPIC_NAME, StringCodec.INSTANCE);
        log.info("AgentTaskManager 初始化, instanceId={}", instanceId);
    }

    /**
     * 获取任务 RBucket
     */
    private RBucket<String> getTaskBucket(String conversationId) {
        return redissonClient.getBucket(TASK_KEY_PREFIX + conversationId, StringCodec.INSTANCE);
    }

    @Override
    public void afterPropertiesSet() {
        // 订阅停止消息
        listenerId = stopTopic.addListener(String.class, (channel, conversationId) -> {
            handleRemoteStop(conversationId);
        });

        // 启动 TTL 刷新定时任务
        ttlRefreshScheduler.scheduleAtFixedRate(
                this::refreshTaskTtls,
                TTL_REFRESH_INTERVAL_MINUTES,
                TTL_REFRESH_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );

        log.info("AgentTaskManager 启动完成, 已订阅停止主题, TTL刷新间隔={}分钟", TTL_REFRESH_INTERVAL_MINUTES);
    }

    @Override
    public void destroy() {
        // 移除发布订阅监听器
        try {
            stopTopic.removeListener(listenerId);
        } catch (Exception e) {
            log.warn("移除发布订阅监听器失败", e);
        }

        // 关闭定时任务
        ttlRefreshScheduler.shutdown();

        // 清理所有本地任务（释放 Redis key）
        for (String conversationId : taskMap.keySet()) {
            doRemoveTask(conversationId);
        }

        log.info("AgentTaskManager 销毁完成, instanceId={}", instanceId);
    }

    /**
     * 任务信息
     */
    public static class TaskInfo {
        private final Sinks.Many<String> sink;
        private Disposable disposable;
        private final long createTime;
        private String agentType;

        public TaskInfo(Sinks.Many<String> sink, String agentType) {
            this.sink = sink;
            this.agentType = agentType;
            this.createTime = System.currentTimeMillis();
        }

        public Sinks.Many<String> getSink() {
            return sink;
        }

        public Disposable getDisposable() {
            return disposable;
        }

        public void setDisposable(Disposable disposable) {
            this.disposable = disposable;
        }

        public long getCreateTime() {
            return createTime;
        }

        public String getAgentType() {
            return agentType;
        }
    }

    /**
     * 注册任务
     *
     * @param conversationId 会话ID
     * @param sink           输出流
     * @param agentType      Agent类型（websearch/file/pptx）
     * @return 任务信息，如果已存在任务则返回null
     */
    public TaskInfo registerTask(String conversationId, Sinks.Many<String> sink, String agentType) {
        // 1. 先检查本地
        TaskInfo existing = taskMap.get(conversationId);
        if (existing != null) {
            log.warn("会话 {} 本地已有任务在执行，拒绝注册新任务", conversationId);
            return null;
        }

        // 2. 尝试在 Redis 中注册
        RBucket<String> bucket = getTaskBucket(conversationId);
        boolean acquired = bucket.trySet(instanceId, TASK_TTL_MINUTES, TimeUnit.MINUTES);

        if (!acquired) {
            String holder = bucket.get();
            log.warn("会话 {} 已在实例 {} 上执行，当前实例 {} 拒绝注册", conversationId, holder, instanceId);
            return null;
        }

        // 3. 注册到本地
        TaskInfo taskInfo = new TaskInfo(sink, agentType);
        taskMap.put(conversationId, taskInfo);
        log.info("注册任务成功: conversationId={}, agentType={}, instanceId={}", conversationId, agentType, instanceId);
        return taskInfo;
    }

    /**
     * 设置任务的Disposable
     *
     * @param conversationId 会话ID
     * @param disposable     Disposable对象
     */
    public void setDisposable(String conversationId, Disposable disposable) {
        TaskInfo taskInfo = taskMap.get(conversationId);
        if (taskInfo != null) {
            taskInfo.setDisposable(disposable);
        }
    }

    /**
     * 停止任务
     * 先检查本地是否有该任务：
     * - 本地有：直接停止
     * - 本地没有：通过 Redis Pub/Sub 广播，让持有该任务的实例执行停止
     */
    public boolean stopTask(String conversationId) {
        // 1. 先尝试本地停止（快速路径）
        TaskInfo localTask = taskMap.get(conversationId);
        if (localTask != null) {
            log.info("本地停止任务: conversationId={}, instanceId={}", conversationId, instanceId);
            doStopTask(conversationId, localTask);
            return true;
        }

        // 2. 先检查 Redis 中是否存在该任务，不存在则无需广播
        RBucket<String> bucket = getTaskBucket(conversationId);
        if (!bucket.isExists()) {
            return false;
        }

        // 3. 持有者是本实例，说明已在处理中，无需广播
        String holder = bucket.get();
        if (instanceId.equals(holder)) {
            log.debug("任务持有者是本实例，跳过广播: conversationId={}", conversationId);
            return false;
        }

        // 4. 本地没有但 Redis 有，且持有者不是本实例，Pub/Sub 广播停止请求
        long receivers = stopTopic.publish(conversationId);
        log.info("发布停止广播: conversationId={}, 订阅者数量={}", conversationId, receivers);
        return true;
    }

    /**
     * 处理远程停止请求（Pub/Sub 回调）
     */
    private void handleRemoteStop(String conversationId) {
        TaskInfo taskInfo = taskMap.remove(conversationId);
        if (taskInfo == null) {
            return;
        }
        log.info("远程停止任务: conversationId={}, instanceId={}", conversationId, instanceId);
        doStopTask(conversationId, taskInfo);
    }

    /**
     * 执行停止任务（本地操作 + Redis 清理）
     */
    private void doStopTask(String conversationId, TaskInfo taskInfo) {
        try {
            // 1. 中断底层调用
            Disposable disposable = taskInfo.getDisposable();
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
                log.info("已中断底层调用: conversationId={}", conversationId);
            }

            // 2. 发送停止消息
            Sinks.Many<String> sink = taskInfo.getSink();
            if (sink != null) {
                try {
                    sink.tryEmitNext(createStopMessage());
                    sink.tryEmitComplete();
                    log.info("已发送停止消息: conversationId={}", conversationId);
                } catch (Exception e) {
                    log.warn("发送停止消息失败: conversationId={}", conversationId, e);
                }
            }
        } finally {
            // 3. 清理本地和 Redis
            doRemoveTask(conversationId);
        }
    }

    /**
     * 内部移除：从本地 map 删除 + 删除 Redis key
     */
    private void doRemoveTask(String conversationId) {
        taskMap.remove(conversationId);

        RBucket<String> bucket = getTaskBucket(conversationId);
        String holder = bucket.get();
        if (instanceId.equals(holder)) {
            bucket.delete();
            log.debug("删除 Redis 任务key: conversationId={}", conversationId);
        }
    }

    /**
     * 检查会话是否有正在执行的任务
     *
     * @param conversationId 会话ID
     * @return 是否有正在执行的任务
     */
    public boolean hasRunningTask(String conversationId) {
        // 先检查本地（快速路径）
        if (taskMap.containsKey(conversationId)) {
            return true;
        }
        // 检查 Redis（其他实例可能持有）
        RBucket<String> bucket = getTaskBucket(conversationId);
        return bucket.isExists();
    }

    /**
     * 定时刷新本地所有运行中任务的 Redis TTL
     * 防止长任务的 key 过期
     */
    private void refreshTaskTtls() {
        if (taskMap.isEmpty()) {
            return;
        }

        log.debug("开始刷新 TTL, 本地任务数={}", taskMap.size());
        for (String conversationId : taskMap.keySet()) {
            try {
                RBucket<String> bucket = getTaskBucket(conversationId);
                String holder = bucket.get();
                if (instanceId.equals(holder)) {
                    bucket.expire(Duration.ofMinutes(TASK_TTL_MINUTES));
                } else {
                    // Redis 中的 holder 不是本实例，说明 key 已被其他实例持有或已过期
                    log.warn("TTL刷新发现 key 归属变化: conversationId={}, 期望={}, 实际={}",
                            conversationId, instanceId, holder);
                    taskMap.remove(conversationId);
                }
            } catch (Exception e) {
                log.error("TTL刷新失败: conversationId={}", conversationId, e);
            }
        }
    }

    /**
     * 创建停止消息
     */
    private String createStopMessage() {
        JSONObject obj = new JSONObject();
        obj.put("type", "text");
        obj.put("content", "⏹ 用户已停止生成\n");
        return JSON.toJSONString(obj);
    }
}
