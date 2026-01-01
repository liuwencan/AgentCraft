package cn.lwccxy.agentcraft.entity.record.pptx;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PPT字段数据结构
 * 用于表示text/image/background类型字段的数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldData {

    /**
     * 字段类型：text/image/background
     */
    private String type;

    /**
     * 字段内容：
     * - text: 文本内容
     * - image: 图片生成提示词
     * - background: 背景布局描述
     */
    private String content;

    /**
     * 字数限制（仅text类型）
     */
    private Integer fontLimit;

    /**
     * 图片URL（仅image和background类型）
     */
    private String url;
}
