package com.guochang.chanpicturebackend.api.aliyunai.Api;


import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.guochang.chanpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.guochang.chanpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.guochang.chanpicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.guochang.chanpicturebackend.common.ErrorCode;
import com.guochang.chanpicturebackend.exception.BusinessException;
import com.guochang.chanpicturebackend.exception.ThrowUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AliyunAiApi {

    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    //创建任务
    // 创建任务地址
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    // 查询任务状态
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";


    public CreateOutPaintingTaskResponse createPaintingTask(CreateOutPaintingTaskRequest request) {
        //1.异常判断
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);

        // 2. 构建JSON请求体
        String jsonBody = JSONUtil.toJsonStr(request);

        // 3. 发送HTTP请求
        try (HttpResponse response = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .execute()) {

            // 4. 检查响应状态
            if (!response.isOk()) {
                throw new RuntimeException("请求失败，状态码: " + response.getStatus());
            }

            // 5. 解析响应体
            String body = response.body();
            return JSONUtil.toBean(body, CreateOutPaintingTaskResponse.class);

        } catch (Exception e) {
            throw new RuntimeException("创建扩图任务失败", e);
        }


    }

    /**
     * 查询创建的任务
     *
     * @param taskId
     * @return
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务 id 不能为空");
        }
        try (HttpResponse httpResponse = HttpRequest.get(String.format(GET_OUT_PAINTING_TASK_URL, taskId))
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                .execute()) {
            if (!httpResponse.isOk()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务失败");
            }
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }
}
