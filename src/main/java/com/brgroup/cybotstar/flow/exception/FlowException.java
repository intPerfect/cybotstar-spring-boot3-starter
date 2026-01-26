package com.brgroup.cybotstar.flow.exception;

import lombok.Getter;

import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flow SDK 错误类
 *
 * 统一的错误类型，包含错误码、原始响应和额外信息
 *
 * @author zhiyuan.xi
 */
@Getter
public class FlowException extends RuntimeException {

    /**
     * 错误码
     */
    private final String code;

    /**
     * 额外信息
     */
    private final Map<String, Object> details;

    /**
     * 错误发生时间
     */
    private final Date timestamp;

    /**
     * Flow 错误码枚举
     */
    public enum FlowErrorCode {
        /** 连接失败 */
        CONNECTION_FAILED("CONNECTION_FAILED"),
        /** 连接超时 */
        CONNECTION_TIMEOUT("CONNECTION_TIMEOUT"),
        /** 连接已关闭 */
        CONNECTION_CLOSED("CONNECTION_CLOSED"),
        /** 发送失败 */
        SEND_FAILED("SEND_FAILED"),
        /** Flow 未运行 */
        NOT_RUNNING("NOT_RUNNING"),
        /** Flow 未等待输入 */
        NOT_WAITING("NOT_WAITING"),
        /** 服务端错误 */
        SERVER_ERROR("SERVER_ERROR"),
        /** Flow 执行错误 */
        FLOW_ERROR("FLOW_ERROR"),
        /** 无效响应 */
        INVALID_RESPONSE("INVALID_RESPONSE"),
        /** 配置错误 */
        INVALID_CONFIG("INVALID_CONFIG"),
        /** 未知错误 */
        UNKNOWN("UNKNOWN");

        private final String value;

        FlowErrorCode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 创建错误实例
     *
     * @param code    错误码（可以是错误码枚举或服务端返回的错误码如 "209"）
     * @param message 错误消息
     * @param details 额外信息，可包含 response 等（可选）
     */
    public FlowException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
        this.timestamp = new Date();
    }

    /**
     * 创建连接失败错误
     */
    public static FlowException connectionFailed(String reason, Throwable originalError) {
        return new FlowException(
                FlowErrorCode.CONNECTION_FAILED.getValue(),
                reason != null ? reason : "WebSocket 连接失败",
                originalError != null ? Map.of("originalError", originalError) : null
        );
    }

    /**
     * 创建连接超时错误
     */
    public static FlowException connectionTimeout(long timeout) {
        return new FlowException(
                FlowErrorCode.CONNECTION_TIMEOUT.getValue(),
                String.format("WebSocket 连接超时 (%dms)", timeout),
                Map.of("timeout", timeout)
        );
    }

    /**
     * 创建发送失败错误
     */
    public static FlowException sendFailed(String reason, Throwable originalError) {
        return new FlowException(
                FlowErrorCode.SEND_FAILED.getValue(),
                reason != null ? reason : "消息发送失败",
                originalError != null ? Map.of("originalError", originalError) : null
        );
    }

    /**
     * 创建 Flow 未运行错误
     */
    public static FlowException notRunning() {
        return new FlowException(
                FlowErrorCode.NOT_RUNNING.getValue(),
                "Flow 对话未运行，无法发送输入",
                null
        );
    }

    /**
     * 创建 Flow 未等待输入错误
     */
    public static FlowException notWaiting() {
        return new FlowException(
                FlowErrorCode.NOT_WAITING.getValue(),
                "Flow 当前未等待用户输入",
                null
        );
    }

    /**
     * 创建连接断开错误
     */
    public static FlowException connectionClosed() {
        return new FlowException(
                FlowErrorCode.CONNECTION_CLOSED.getValue(),
                "Flow 连接已断开",
                null
        );
    }

    /**
     * 创建服务端错误
     *
     * @param message    错误消息（如 "系统异常(209),稍后再试"）
     * @param serverCode 服务端错误码（如 "209"），会作为 code 字段
     * @param response   原始响应
     */
    public static FlowException serverError(String message, String serverCode, Object response) {
        return new FlowException(
                serverCode,
                message,
                response != null ? Map.of("response", response) : null
        );
    }

    /**
     * 创建 Flow 执行错误
     */
    public static FlowException flowError(String message, Object response) {
        return new FlowException(
                FlowErrorCode.FLOW_ERROR.getValue(),
                message,
                response != null ? Map.of("response", response) : null
        );
    }

    /**
     * 创建配置错误
     */
    public static FlowException invalidConfig(String field, String reason) {
        return new FlowException(
                FlowErrorCode.INVALID_CONFIG.getValue(),
                String.format("配置错误: %s - %s", field, reason != null ? reason : "无效值"),
                Map.of("field", field)
        );
    }

    /**
     * 从消息中创建服务端错误（自动提取错误码）
     *
     * @param message  错误消息（如 "系统异常(209),稍后再试"）
     * @param response 原始响应
     */
    public static FlowException fromServerMessage(String message, Object response) {
        // 从消息中提取错误码（如 "209"）
        Pattern pattern = Pattern.compile("\\((\\d{3,})\\)");
        Matcher matcher = pattern.matcher(message);
        String serverCode = matcher.find() ? matcher.group(1) : FlowErrorCode.SERVER_ERROR.getValue();
        return new FlowException(serverCode, message, response != null ? Map.of("response", response) : null);
    }

    /**
     * 包装未知错误
     */
    public static FlowException wrap(Throwable error) {
        if (error instanceof FlowException) {
            return (FlowException) error;
        }
        if (error instanceof Exception) {
            return new FlowException(
                    FlowErrorCode.UNKNOWN.getValue(),
                    error.getMessage(),
                    Map.of("originalError", error)
            );
        }
        return new FlowException(
                FlowErrorCode.UNKNOWN.getValue(),
                String.valueOf(error),
                Map.of("rawError", error)
        );
    }
}

