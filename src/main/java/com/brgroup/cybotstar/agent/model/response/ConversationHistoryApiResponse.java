package com.brgroup.cybotstar.agent.model.response;

import lombok.Data;

/**
 * 获取会话历史的 API 响应包装类
 *
 * @author zhiyuan.xi
 */
@Data
public class ConversationHistoryApiResponse {
    /**
     * 响应码（000000 表示成功）
     */
    private String code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private ConversationHistoryResponse data;
}

