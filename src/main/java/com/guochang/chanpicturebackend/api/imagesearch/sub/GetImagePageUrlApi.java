package com.guochang.chanpicturebackend.api.imagesearch.sub;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.guochang.chanpicturebackend.common.ErrorCode;
import com.guochang.chanpicturebackend.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class GetImagePageUrlApi {

    public static String getImagePageUrl(String imageUrl) {
        // 1. 准备请求参数
        Map<String, Object> formData = new HashMap<>();
        formData.put("image", imageUrl);
        formData.put("tn", "pc");
        formData.put("from", "pc");
        formData.put("image_source", "PC_UPLOAD_URL");
        long uptime = System.currentTimeMillis();
        String url = "https://graph.baidu.com/upload?uptime=" + uptime;

        try {
            log.info("调用百度图搜接口，URL: {}, 图片URL: {}", url, imageUrl);

            // 2. 发送 POST 请求到百度接口
            HttpResponse response = HttpRequest.post(url)
                    .form(formData)
                    .timeout(10000)  // 增加超时时间
                    .execute();

            int status = response.getStatus();
            String responseBody = response.body();

            log.info("百度图搜接口响应状态: {}, 响应体: {}", status, responseBody);

            // 判断响应状态
            if (HttpStatus.HTTP_OK != status) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "接口调用失败，状态码: " + status);
            }

            // 解析响应
            Map<String, Object> result = JSONUtil.toBean(responseBody, Map.class);

            if (result == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口返回数据为空");
            }

            // 检查状态码
            Object statusObj = result.get("status");
            if (statusObj == null || !Integer.valueOf(0).equals(statusObj)) {
                String errorMsg = "接口返回错误，状态: " + statusObj;
                log.error("百度图搜接口错误: {}", errorMsg);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, errorMsg);
            }

            // 获取数据
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            if (data == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口返回数据格式错误");
            }

            String rawUrl = (String) data.get("url");
            if (StrUtil.isBlank(rawUrl)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未返回有效URL");
            }

            // 对 URL 进行解码
            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            log.info("百度图搜成功，结果URL: {}", searchResultUrl);

            return searchResultUrl;
        } catch (BusinessException e) {
            throw e; // 重新抛出业务异常
        } catch (Exception e) {
            log.error("搜索失败，图片URL: {}", imageUrl, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败: " + e.getMessage());
        }
    }
}