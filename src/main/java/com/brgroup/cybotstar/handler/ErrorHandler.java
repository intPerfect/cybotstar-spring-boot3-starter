package com.brgroup.cybotstar.handler;

import com.brgroup.cybotstar.agent.session.SessionContext;
import com.brgroup.cybotstar.agent.exception.AgentException;
import com.brgroup.cybotstar.model.ws.WSResponse;
import com.brgroup.cybotstar.util.FormatUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 错误处理器
 * 统一处理各种类型的错误，包括 WebSocket 响应错误、网络异常、超时异常等
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class ErrorHandler {

    /**
     * 处理错误响应（WebSocket 服务器返回的错误）
     *
     * @param response  WebSocket 响应消息对象
     * @param sessionId 会话 ID
     * @param callbacks 回调函数集合
     */
    public void handleError(@NonNull WSResponse response, @NonNull String sessionId, SessionContext.@Nullable AgentCallbacks callbacks) {
        String code = response.getCode();
        String message = response.getMessage() != null ? response.getMessage() : "未知错误";

        log.error("Received error response, sessionId: {}, code: {}, message: {}",
                FormatUtils.formatSessionId(sessionId), code, message);

        AgentException error = AgentException.requestFailed(
                String.format("Websocket 服务器返回错误: code=%s, message=%s", code, message));

        triggerErrorCallback(error, callbacks);
    }

    /**
     * 处理异常（网络异常、超时异常、解析异常等）
     *
     * @param exception 异常对象
     * @param sessionId 会话 ID
     * @param callbacks 回调函数集合
     */
    public void handleException(@NonNull Throwable exception, @NonNull String sessionId, SessionContext.@Nullable AgentCallbacks callbacks) {
        // 包装异常，确保是 AgentException 类型
        AgentException error = exception instanceof AgentException
                ? (AgentException) exception
                : AgentException.wrap(exception);

        log.error("Exception occurred, sessionId: {}, error: {}",
                FormatUtils.formatSessionId(sessionId), error.getMessage(), exception);

        triggerErrorCallback(error, callbacks);
    }

    /**
     * 处理错误（统一入口）
     * 根据错误类型自动选择合适的处理方法
     *
     * @param error     错误对象（可以是 AgentException、WSResponse 或其他异常）
     * @param sessionId 会话 ID
     * @param callbacks 回调函数集合
     */
    public void handle(@Nullable Object error, @NonNull String sessionId, SessionContext.@Nullable AgentCallbacks callbacks) {
        if (error == null) {
            log.warn("Received null error, sessionId: {}", FormatUtils.formatSessionId(sessionId));
            return;
        }

        if (error instanceof WSResponse) {
            handleError((WSResponse) error, sessionId, callbacks);
        } else if (error instanceof Throwable) {
            handleException((Throwable) error, sessionId, callbacks);
        } else {
            log.warn("Unknown error type: {}, sessionId: {}", error.getClass().getName(),
                    FormatUtils.formatSessionId(sessionId));
            AgentException wrappedError = AgentException.requestFailed("未知错误类型: " + error);
            triggerErrorCallback(wrappedError, callbacks);
        }
    }

    /**
     * 触发错误回调
     * 统一处理错误回调的触发，确保回调执行失败不会影响主流程
     *
     * @param error     错误对象
     * @param callbacks 回调函数集合
     */
    private void triggerErrorCallback(@NonNull AgentException error, SessionContext.@Nullable AgentCallbacks callbacks) {
        if (callbacks != null && callbacks.getOnError() != null) {
            try {
                callbacks.getOnError().accept(error);
            } catch (Exception callbackException) {
                // 回调执行失败不应该影响主流程，只记录日志
                log.error("Error callback execution failed", callbackException);
            }
        }
    }
}

