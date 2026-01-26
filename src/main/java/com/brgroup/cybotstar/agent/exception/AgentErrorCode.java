package com.brgroup.cybotstar.agent.exception;

/**
 * Agent 错误码枚举
 *
 * @author zhiyuan.xi
 */
public enum AgentErrorCode {
    /**
     * 连接失败
     */
    CONNECTION_FAILED,

    /**
     * 连接超时
     */
    CONNECTION_TIMEOUT,

    /**
     * 连接已关闭
     */
    CONNECTION_CLOSED,

    /**
     * 发送失败
     */
    SEND_FAILED,

    /**
     * 响应超时
     */
    RESPONSE_TIMEOUT,

    /**
     * 无效响应
     */
    INVALID_RESPONSE,

    /**
     * 配置错误
     */
    INVALID_CONFIG,

    /**
     * 请求失败
     */
    REQUEST_FAILED,

    /**
     * 会话错误
     */
    SESSION_ERROR,

    /**
     * 未知错误
     */
    UNKNOWN
}

