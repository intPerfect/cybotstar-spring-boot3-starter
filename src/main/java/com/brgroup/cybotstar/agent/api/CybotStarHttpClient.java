package com.brgroup.cybotstar.agent.api;

import com.brgroup.cybotstar.agent.model.ConversationHistoryApiResponse;
import com.brgroup.cybotstar.agent.model.GetConversationHistoryRequest;
import com.dtflys.forest.annotation.*;
import com.dtflys.forest.http.ForestResponse;

/**
 * CybotStar HTTP 客户端接口
 * 基于 Forest 库实现的声明式 HTTP 客户端
 *
 * @author zhiyuan.xi
 */
@BaseRequest(contentType = "application/json")
public interface CybotStarHttpClient {

    /**
     * 获取智能体会话历史
     *
     * @param baseUrl    基础 URL（完整路径，例如：https://www.cybotstar.cn/openapi/v2）
     * @param robotKey   机器人 Key
     * @param robotToken 机器人 Token
     * @param username   用户名（用于请求头）
     * @param request    请求体
     * @return 响应数据
     */
    @Post("${baseUrl}/conversation/segment/get_list/")
    ForestResponse<ConversationHistoryApiResponse> getConversationHistory(
            @Var("baseUrl") String baseUrl,
            @Header("cybertron-robot-key") String robotKey,
            @Header("cybertron-robot-token") String robotToken,
            @Header("username") String username,
            @Body GetConversationHistoryRequest request);
}
