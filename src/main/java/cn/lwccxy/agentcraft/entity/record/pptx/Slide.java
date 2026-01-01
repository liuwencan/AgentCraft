package cn.lwccxy.agentcraft.entity.record.pptx;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * PPT单页数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Slide {

    /**
     * 页面类型
     */
    private String pageType;

    /**
     * 页面描述
     */
    private String pageDesc;

    /**
     * 页面索引（模板页码）
     */
    private Integer templatePageIndex;

    /**
     * 页面数据（字段名 -> 字段数据）
     */
    private Map<String, FieldData> data;
}
