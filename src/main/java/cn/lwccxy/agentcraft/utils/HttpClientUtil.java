package cn.lwccxy.agentcraft.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Slf4j
public class HttpClientUtil {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 发送POST请求
     *
     * @param url 请求URL
     * @param headers 请求头
     * @param body 请求体
     * @return 响应结果
     */
    public static String doPost(String url, Map<String, String> headers, Object body) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5)); // 设置超时时间为5分钟

            // 添加自定义请求头
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }

            // 添加请求体
            String bodyStr = body instanceof String ? (String) body : JSON.toJSONString(body);
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8));

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                log.error("HTTP请求失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                throw new RuntimeException("HTTP请求失败，状态码: " + response.statusCode() + ", 响应: " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.error("HTTP请求异常", e);
            Thread.currentThread().interrupt(); // 重要：恢复中断状态
            throw new RuntimeException("HTTP请求异常", e);
        }
    }

    /**
     * 发送GET请求
     *
     * @param url 请求URL
     * @param headers 请求头
     * @return 响应结果
     */
    public static String doGet(String url, Map<String, String> headers) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5));

            // 添加请求头
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }

            HttpRequest request = requestBuilder.GET().build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                log.error("HTTP请求失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                throw new RuntimeException("HTTP请求失败，状态码: " + response.statusCode() + ", 响应: " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.error("HTTP请求异常", e);
            Thread.currentThread().interrupt(); // 重要：恢复中断状态
            throw new RuntimeException("HTTP请求异常", e);
        }
    }

    /**
     * URL编码
     */
    public static String urlEncode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }
}