package com.brgroup.cybotstar.agent.api;

import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.agent.exception.AgentException;
import com.brgroup.cybotstar.agent.model.ConversationHistoryApiResponse;
import com.brgroup.cybotstar.agent.model.ConversationHistoryResponse;
import com.brgroup.cybotstar.agent.model.GetConversationHistoryOptions;
import com.brgroup.cybotstar.agent.model.GetConversationHistoryRequest;
import com.dtflys.forest.http.ForestResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent API 客户端
 * 负责处理与 Agent 服务相关的 HTTP API 调用
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class AgentApiClient {

    private final CybotStarHttpClient httpClient;
    private final AgentConfig config;

    public AgentApiClient(AgentConfig config, CybotStarHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;

        // 验证 HTTP 配置
        if (config.getHttp() == null || config.getHttp().getUrl() == null || config.getHttp().getUrl().isEmpty()) {
            throw new IllegalArgumentException("HTTP URL 未配置，请在配置中设置 agent.http.url");
        }
    }

    /**
     * 获取智能体会话历史
     *
     * @param options 查询选项
     * @return 会话历史响应数据
     * @throws AgentException 请求失败时抛出异常
     */
    public ConversationHistoryResponse getConversationHistory(GetConversationHistoryOptions options) {
        try {
            // 构建请求实体
            GetConversationHistoryRequest.GetConversationHistoryRequestBuilder requestBuilder = GetConversationHistoryRequest.builder()
                    .username(config.getCredentials().getUsername());

            if (options != null) {
                requestBuilder
                        .filterMode(options.getFilterMode())
                        .filterUserCode(options.getFilterUserCode())
                        .createStartTime(options.getCreateStartTime())
                        .createEndTime(options.getCreateEndTime())
                        .messageSource(options.getMessageSource())
                        .segmentCodeList(options.getSegmentCodeList())
                        .page(options.getPage() != null ? options.getPage() : 1)
                        .pagesize(options.getPagesize() != null ? options.getPagesize() : 10);
            }

            GetConversationHistoryRequest request = requestBuilder.build();

            // 发送请求
            String baseUrl = config.getHttp().getUrl();
            // 移除 baseUrl 末尾的 /，路径拼接时会自动添加
            if (baseUrl != null && baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            ForestResponse<ConversationHistoryApiResponse> forestResponse = httpClient.getConversationHistory(
                    baseUrl,
                    config.getCredentials().getRobotKey(),
                    config.getCredentials().getRobotToken(),
                    config.getCredentials().getUsername(),
                    request);

            if (!forestResponse.isSuccess()) {
                String errorMsg = "HTTP 请求失败: " + forestResponse.getStatusCode();
                try {
                    String content = forestResponse.getContent();
                    if (content != null && !content.isEmpty()) {
                        errorMsg += " - " + content;
                    }
                } catch (Exception e) {
                    // 忽略获取内容的异常
                }
                throw AgentException.requestFailed(errorMsg);
            }

            ConversationHistoryApiResponse apiResponse = forestResponse.getResult();
            if (apiResponse == null) {
                throw AgentException.requestFailed("响应数据为空");
            }

            // 检查 API 响应码
            String code = apiResponse.getCode();
            if (code != null && !code.equals("000000") && !code.startsWith("2")) {
                throw AgentException.requestFailed(
                        apiResponse.getMessage() != null ? apiResponse.getMessage() : "获取会话历史失败");
            }

            // 返回数据
            ConversationHistoryResponse data = apiResponse.getData();
            if (data == null) {
                throw AgentException.requestFailed("响应数据为空");
            }

            return data;

        } catch (Exception e) {
            if (e instanceof AgentException) {
                throw e;
            }
            throw AgentException.requestFailed("获取会话历史失败", e);
        }
    }
}
