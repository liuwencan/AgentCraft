package cn.lwccxy.agentcraft.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送短信验证码请求参数
 */
@Data
public class SendCodeDTO {

    /**
     * 手机号（中国大陆 11 位）
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
