package cn.lwccxy.agentcraft.sms;
import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短信发送工具类
 **/
@Component
@Slf4j
public class AliyunSmsHelper {

    @Resource
    private Client client;

    /**
     * 发送短信
     * @param signName 签名
     * @param templateCode 模板码
     * @param phone 目标手机号
     * @param templateParam 模板参数
     * @return
     */
    public boolean sendMessage(String signName, String templateCode, String phone, String templateParam) {

        SendSmsVerifyCodeRequest sendSmsVerifyCodeRequest = new SendSmsVerifyCodeRequest()
                .setSignName(signName)
                .setTemplateCode(templateCode)
//                .setTemplateParam("{\"code\":\"1234\",\"min\":\"5\"}")
                .setTemplateParam(templateParam)
                .setPhoneNumber(phone);
        RuntimeOptions runtime = new RuntimeOptions();
        try {
            log.info("==> 开始短信发送, phone: {}, signName: {}, templateCode: {}, templateParam: {}", phone, signName, templateCode, templateParam);

            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCodeWithOptions(sendSmsVerifyCodeRequest, runtime);

            log.info("==> 短信发送成功, response: {}", response);
            return true;
        } catch (Exception error) {
            log.error("==> 短信发送错误: ", error);
            return false;
        }
    }
}

