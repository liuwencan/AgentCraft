package cn.lwccxy.agentcraft.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 幻灯片图片信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideImage {

    /**
     * 页码
     */
    private Integer page;

    /**
     * 图片URL
     */
    private String url;

    /**
     * 图片提示词（用于生成图片）
     */
    private String imagePrompt;

    /**
     * 图片在页面中的位置（可选，如"顶部"、"底部"、"左侧"、"右侧"）
     */
    private String position;

    /**
     * 图片描述（可选）
     */
    private String description;

    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        return JSONObject.toJSONString(this);
    }

    /**
     * 从JSON字符串创建对象
     */
    public static SlideImage fromJson(String json) {
        return JSONObject.parseObject(json, SlideImage.class);
    }

    /**
     * 转换为List<SlideImage>
     */
    public static List<SlideImage> fromJsonList(String json) {
        return JSON.parseArray(json, SlideImage.class);
    }

    /**
     * 转换为JSON字符串
     */
    public static String toJson(List<SlideImage> images) {
        return JSONObject.toJSONString(images);
    }
}
