package com.brgroup.cybotstar.core.exception;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 通用错误处理器
 *
 * 负责统一处理错误，包括错误日志记录和错误转换。
 * 不依赖特定的回调接口，适用于所有 Client。
 *
 * 主要职责：
 * 1. 错误转换：将各种错误类型统一转换为标准错误
 * 2. 日志记录：根据错误类型选择合适的日志级别
 * 3. 错误包装：提供便捷的错误包装和创建方法
 *
 * 设计原则：
 * - 职责单一：只处理错误相关的逻辑，不涉及业务逻辑
 * - 通用性：不依赖特定的回调接口
 * - 可配置：支持控制是否记录日志等
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class GenericErrorHandler {

    /**
     * 错误处理选项
     */
    @Setter
    @Getter
    public static class ErrorHandleOptions {
        /**
         * 是否记录日志
         * - true: 记录错误日志（默认）
         * - false: 不记录日志
         */
        private Boolean logError = true;

        /**
         * 错误上下文信息
         * 用于在日志中添加上下文信息，便于调试
         * 例如：{ sessionId: 'xxx', method: 'sendInternal' }
         */
        private Map<String, Object> context;

        /**
         * 错误码（可选），用于日志分类
         */
        private String errorCode = "UNKNOWN";

        /**
         * 错误级别（可选），用于日志级别选择
         * - 'warn': 警告级别
         * - 'error': 错误级别（默认）
         */
        private String level = "error";

    }

    /**
     * 处理错误
     *
     * 这是错误处理的主方法，统一处理所有错误。
     *
     * 处理流程：
     * 1. 提取处理选项（日志、上下文）
     * 2. 记录错误日志（如果启用）
     *
     * @param error   错误对象，可以是 Error 或自定义错误
     * @param options 处理选项（可选），控制是否记录日志、上下文等
     */
    public void handle(Throwable error, ErrorHandleOptions options) {
        if (options == null) {
            options = new ErrorHandleOptions();
        }

        // 提取处理选项，设置默认值
        boolean logError = options.getLogError() != null ? options.getLogError() : true;
        Map<String, Object> context = options.getContext() != null ? options.getContext() : Map.of();
        String errorCode = options.getErrorCode() != null ? options.getErrorCode() : "UNKNOWN";
        String level = options.getLevel() != null ? options.getLevel() : "error";

        // 记录错误日志
        if (logError) {
            logError(error, context, errorCode, level);
        }
    }

    /**
     * 处理错误（简化版本，使用默认选项）
     *
     * @param error 错误对象
     */
    public void handle(Throwable error) {
        handle(error, null);
    }

    /**
     * 创建带上下文的错误处理选项
     * 静态工厂方法，简化 ErrorHandleOptions 的创建
     *
     * @param context 错误上下文信息
     * @return ErrorHandleOptions 实例
     */
    public static ErrorHandleOptions withContext(Map<String, Object> context) {
        ErrorHandleOptions options = new ErrorHandleOptions();
        options.setContext(context);
        return options;
    }

    /**
     * 创建带上下文和错误码的错误处理选项
     *
     * @param context   错误上下文信息
     * @param errorCode 错误码
     * @return ErrorHandleOptions 实例
     */
    public static ErrorHandleOptions withContextAndCode(Map<String, Object> context, String errorCode) {
        ErrorHandleOptions options = new ErrorHandleOptions();
        options.setContext(context);
        options.setErrorCode(errorCode);
        return options;
    }

    /**
     * 创建带上下文和级别的错误处理选项
     *
     * @param context 错误上下文信息
     * @param level   错误级别（warn/error）
     * @return ErrorHandleOptions 实例
     */
    public static ErrorHandleOptions withContextAndLevel(Map<String, Object> context, String level) {
        ErrorHandleOptions options = new ErrorHandleOptions();
        options.setContext(context);
        options.setLevel(level);
        return options;
    }

    /**
     * 记录错误日志
     *
     * 根据错误级别选择合适的日志级别：
     * - 'warn': 可能是临时问题，如网络问题、超时等
     * - 'error': 严重问题，如配置错误、数据错误等
     *
     * @param error    错误对象
     * @param context  上下文信息，会添加到日志中
     * @param errorCode 错误码，用于日志分类
     * @param level    错误级别（warn/error）
     */
    private void logError(Throwable error, Map<String, Object> context, String errorCode, String level) {
        // 构建日志数据对象
        // 包含错误的所有相关信息，便于调试和问题排查
        String message = String.format("[%s] %s", errorCode, error.getMessage());
        if (context != null && !context.isEmpty()) {
            message += " Context: " + context;
        }

        // 根据错误级别选择日志方法
        if ("warn".equals(level)) {
            log.warn(message, error);
        } else {
            log.error(message, error);
        }
    }
}

