package cn.lwccxy.agentcraft.agent.pptx.strategy;

import cn.lwccxy.agentcraft.entity.record.pptx.AiPptInst;
import cn.lwccxy.agentcraft.entity.record.pptx.PptInstStatus;
import reactor.core.publisher.Sinks;

/**
 * PPT状态策略接口
 * 使用策略模式处理不同状态的处理逻辑
 */
public interface PptStateStrategy {

    /**
     * 执行该状态的处理逻辑
     *
     * @param inst            PPT实例
     * @param sink            响应流
     * @param query           用户查询
     * @param thinkingBuffer  思考缓冲区
     * @param context         策略上下文
     */
    void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                 StringBuilder thinkingBuffer, PptStateStrategyContext context);

    /**
     * 获取该策略对应的目标状态
     * 执行成功后，状态应该变为这个状态
     *
     * @return 目标状态
     */
    PptInstStatus getTargetStatus();
}
