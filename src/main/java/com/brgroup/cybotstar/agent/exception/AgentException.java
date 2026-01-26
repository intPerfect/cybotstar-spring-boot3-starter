package com.brgroup.cybotstar.agent.exception;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Agent SDK 异常类
 * 对应 TypeScript 的 AgentError
 *
 * @author zhiyuan.xi
 */
@Getter
public class AgentException extends RuntimeException {
    /**
     * 错误码
     */
    private final AgentErrorCode code;

    /**
     * 原始异常
     */
    private final Throwable originalError;

    /**
     * 额外信息
     */
    private final Object details;

    /**
     * 错误发生时间
     */
    private final LocalDateTime timestamp;

    public AgentException(AgentErrorCode code, String message) {
        this(code, message, null, null);
    }

    public AgentException(AgentErrorCode code, String message, Throwable originalError) {
        this(code, message, originalError, null);
    }

    public AgentException(AgentErrorCode code, String message, Throwable originalError, Object details) {
        super(message, originalError);
        this.code = code;
        this.originalError = originalError;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 创建连接失败异常
     */
    public static AgentException connectionFailed(String reason) {
        return new AgentException(AgentErrorCode.CONNECTION_FAILED, reason != null ? reason : "WebSocket 连接失败");
    }

    /**
     * 创建连接失败异常（带原始异常）
     */
    public static AgentException connectionFailed(String reason, Throwable originalError) {
        return new AgentException(AgentErrorCode.CONNECTION_FAILED, reason != null ? reason : "WebSocket 连接失败", originalError);
    }

    /**
     * 创建连接超时异常
     */
    public static AgentException connectionTimeout(long timeout) {
        return new AgentException(AgentErrorCode.CONNECTION_TIMEOUT, String.format("WebSocket 连接超时 (%dms)", timeout));
    }

    /**
     * 创建发送失败异常
     */
    public static AgentException sendFailed(String reason) {
        return new AgentException(AgentErrorCode.SEND_FAILED, reason != null ? reason : "消息发送失败");
    }

    /**
     * 创建发送失败异常（带原始异常）
     */
    public static AgentException sendFailed(String reason, Throwable originalError) {
        return new AgentException(AgentErrorCode.SEND_FAILED, reason != null ? reason : "消息发送失败", originalError);
    }

    /**
     * 创建响应超时异常
     */
    public static AgentException responseTimeout(long timeout) {
        return new AgentException(AgentErrorCode.RESPONSE_TIMEOUT, String.format("等待响应超时 (%dms)", timeout));
    }

    /**
     * 创建无效响应异常
     */
    public static AgentException invalidResponse(Object response) {
        return new AgentException(AgentErrorCode.INVALID_RESPONSE, "收到无效的响应数据", null, response);
    }

    /**
     * 创建配置错误异常
     */
    public static AgentException invalidConfig(String field, String reason) {
        return new AgentException(AgentErrorCode.INVALID_CONFIG, String.format("配置错误: %s - %s", field, reason != null ? reason : "无效值"));
    }

    /**
     * 创建请求失败异常
     */
    public static AgentException requestFailed(String reason) {
        return new AgentException(AgentErrorCode.REQUEST_FAILED, reason);
    }

    /**
     * 创建请求失败异常（带原始异常）
     */
    public static AgentException requestFailed(String reason, Throwable originalError) {
        return new AgentException(AgentErrorCode.REQUEST_FAILED, reason, originalError);
    }

    /**
     * 创建会话错误异常
     */
    public static AgentException sessionError(String reason) {
        return new AgentException(AgentErrorCode.SESSION_ERROR, reason);
    }

    /**
     * 包装未知异常
     */
    public static AgentException wrap(Throwable error) {
        if (error instanceof AgentException) {
            return (AgentException) error;
        }
        return new AgentException(AgentErrorCode.UNKNOWN, error.getMessage(), error);
    }
}

