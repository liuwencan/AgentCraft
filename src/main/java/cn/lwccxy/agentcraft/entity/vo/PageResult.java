package cn.lwccxy.agentcraft.entity.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 分页结果
 */
@Data
@Builder
public class PageResult<T> {
    /**
     * 页码
     */
    private Integer pageNum;

    /**
     * 页大小
     */
    private Integer pageSize;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 记录列表
     */
    private List<T> records;
}
