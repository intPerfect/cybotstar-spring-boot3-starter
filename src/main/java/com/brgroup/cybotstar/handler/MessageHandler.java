package com.brgroup.cybotstar.handler;

import com.brgroup.cybotstar.agent.session.SessionContext;
import com.brgroup.cybotstar.stream.StreamManager;
import com.brgroup.cybotstar.stream.StreamState;
import com.brgroup.cybotstar.model.common.ResponseIndex;
import com.brgroup.cybotstar.model.common.ResponseType;
import com.brgroup.cybotstar.model.common.SessionState;
import com.brgroup.cybotstar.model.ws.WSResponse;
import com.brgroup.cybotstar.model.ws.WSResponseData;
import com.brgroup.cybotstar.util.CybotStarUtils;
import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 消息处理器类
 * 处理 WebSocket 消息的解析和分发逻辑
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class MessageHandler {

    @NonNull
    private final StreamManager streamManager;
    @NonNull
    private final ErrorHandler errorHandler;

    public MessageHandler(@NonNull StreamManager streamManager, @NonNull ErrorHandler errorHandler) {
        this.streamManager = streamManager;
        this.errorHandler = errorHandler;
    }

    /**
     * 消息处理结果
     */
    @Data
    @AllArgsConstructor
    public static class MessageHandleResult {
        private boolean shouldContinue;
        private boolean isFinal;
        private String text;
        private boolean hasContent;
    }

    /**
     * 处理 WebSocket 消息
     *
     * @param response  WebSocket 响应消息对象
     * @param sessionId 会话 ID
     * @param state     流式状态对象
     * @param callbacks 回调函数集合
     * @return 消息处理结果
     */
    @NonNull
    public MessageHandleResult handle(@NonNull WSResponse response, @NonNull String sessionId, @NonNull StreamState state,
            SessionContext.@Nullable AgentCallbacks callbacks) {
        String finishFlag = response.getFinish();
        String respType = response.getType();
        Integer respIndex = response.getIndex();
        String respCode = response.getCode();

        log.debug("Received WS message, index={}, code={}, finish={}", respIndex, respCode, finishFlag);

        // 触发原始响应回调
        if (callbacks != null && callbacks.getOnRawResponse() != null) {
            try {
                callbacks.getOnRawResponse().accept(response);
            } catch (Exception e) {
                log.debug("Error in onRawResponse callback", e);
            }
        }

        // 处理特殊索引消息
        if (respIndex != null) {
            Optional<ResponseIndex> indexType = ResponseIndex.fromValue(respIndex);
            if (indexType.isPresent()) {
                ResponseIndex index = indexType.get();
                return switch (index) {
                    case THREAD_INFO -> {
                        // 线程信息，忽略
                        log.debug("Ignoring thread info message");
                        yield new MessageHandleResult(false, false, "", false);
                    }
                    case MESSAGE_CONFIRMED -> {
                        // 消息确认
                        log.debug("Received message confirmation");
                        yield new MessageHandleResult(false, false, "", false);
                    }
                    case ONLINE_SEARCH, IMAGE_REFERENCE -> {
                        // 特殊响应类型（联网搜索、图片）
                        handleSpecialResponse(response, sessionId, callbacks);
                        yield new MessageHandleResult(false, false, "", false);
                    }
                    case REASONING -> {
                        // Reasoning（思考内容）
                        handleReasoningResponse(response, sessionId, state, callbacks);
                        yield new MessageHandleResult(false, false, "", false);
                    }
                };
            }
        }

        // 检查发送状态
        if (!state.getSendState().isSending()) {
            log.debug("Ignoring response (no request in progress)");
            return new MessageHandleResult(false, false, "", false);
        }

        // 检查错误码
        if (respCode != null && !"000000".equals(respCode)) {
            log.warn("Error response, code={}", respCode);
            handleErrorResponse(response, sessionId, state, callbacks);
            return new MessageHandleResult(false, false, "", false);
        }

        // 提取文本内容
        String text = extractText(response);
        boolean isFinal = "y".equals(finishFlag) || ResponseType.isType(respType, ResponseType.LLM_END);
        boolean hasContent = text != null && !text.isEmpty();

        return new MessageHandleResult(true, isFinal, text, hasContent);
    }

    /**
     * 提取文本内容（公开方法，供外部调用）
     */
    @NonNull
    public String extractText(@NonNull WSResponse response) {
        Object data = response.getData();
        if (data == null) {
            return "";
        }

        // 情况1: data 是字符串
        if (data instanceof String) {
            return (String) data;
        }

        // 情况2: 如果是 reasoning 类型，提取 content 字段
        String respType = response.getType();
        if ("reasoning".equals(respType)) {
            try {
                String jsonStr = JSON.toJSONString(data);
                @SuppressWarnings("unchecked")
                Map<String, Object> map = JSON.parseObject(jsonStr, Map.class);
                if (map != null && map.containsKey("content")) {
                    Object content = map.get("content");
                    return content != null ? content.toString() : "";
                }
            } catch (Exception e) {
                log.debug("Failed to parse reasoning data", e);
            }
        }

        // 情况3: data 是对象且包含 answer 字段
        if (data instanceof WSResponseData responseData) {
            return responseData.getAnswer() != null ? responseData.getAnswer() : "";
        }

        // 情况4: 尝试解析为 JSON 对象
        try {
            String jsonStr = JSON.toJSONString(data);
            WSResponseData responseData = JSON.parseObject(jsonStr, WSResponseData.class);
            if (responseData != null && responseData.getAnswer() != null) {
                return responseData.getAnswer();
            }

            // 如果解析后没有 answer 字段，尝试直接获取 data 字段
            @SuppressWarnings("unchecked")
            Map<String, Object> map = JSON.parseObject(jsonStr, Map.class);
            if (map != null && map.containsKey("answer")) {
                Object answer = map.get("answer");
                return answer != null ? answer.toString() : "";
            }

            // 如果都没有，返回空字符串
            return "";
        } catch (Exception e) {
            log.debug("Failed to parse response data, trying to process as string", e);
            // 如果解析失败，尝试直接转换为字符串
            return data.toString();
        }
    }

    /**
     * 刷新超时定时器（统一方法）
     * 当收到新的 chunk 时，刷新超时定时器，避免在流式输出过程中超时
     * 
     * @param state 流式状态对象
     * @param sessionId 会话 ID
     */
    private void refreshTimeoutIfNeeded(@NonNull StreamState state, @NonNull String sessionId) {
        if (state.getSendState().isSending() && state.getTimeout() > 0 && state.getRefreshTimeout() != null) {
            state.getRefreshTimeout().run();
            log.debug("Refreshing timeout timer, sessionId: {}, timeout: {}ms",
                    CybotStarUtils.formatSessionId(sessionId), state.getTimeout());
        }
    }

    /**
     * 追加流式内容
     */
    public void appendStreamContent(@NonNull String text, @NonNull String sessionId, @NonNull StreamState state,
            SessionContext.@Nullable AgentCallbacks callbacks) {
        // 刷新超时定时器：当收到新的 chunk 时，刷新超时定时器，避免在流式输出过程中超时
        // 这样可以支持长时间的流式响应
        refreshTimeoutIfNeeded(state, sessionId);

        // 更新缓冲区
        if (state.getStreamBuffer().getMsgId() == null || state.getStreamBuffer().getMsgId().isEmpty()) {
            state.getStreamBuffer().setMsgId(CybotStarUtils.generateMessageId("stream"));
            state.getStreamBuffer().getBuffer().setLength(0);
        }
        state.getStreamBuffer().getBuffer().append(text);

        // 触发流式回调
        if (state.getStreamConfig().isCurrentStream() && state.getCallbacks().getCurrentOnChunk() != null) {
            state.getCallbacks().getCurrentOnChunk().accept(text);
        } else if (callbacks != null && callbacks.getOnChunk() != null) {
            callbacks.getOnChunk().accept(text);
        }

        // 推送到流队列
        if (streamManager.isActive(sessionId)) {
            StreamManager.StreamQueueItem item = new StreamManager.StreamQueueItem();
            item.setValue(text);
            item.setDone(false);
            streamManager.enqueue(sessionId, item);
        }
    }

    /**
     * 处理消息完成
     */
    public void handleMessageComplete(@Nullable String text, boolean hasContent, @NonNull String sessionId,
            @NonNull StreamState state, SessionContext.@Nullable AgentCallbacks callbacks) {
        // 检查是否已经处理过完成消息（避免重复处理）
        if (!state.getSendState().isSending()) {
            log.debug("Ignoring duplicate completion message (request already completed)");
            return;
        }

        // 保存回调引用（状态重置前保存）
        Consumer<String> currentOnComplete = state.getCallbacks().getCurrentOnComplete();
        boolean isStreamMode = state.getStreamConfig().isCurrentStream();

        // 非流式模式：直接使用完成消息的完整文本，不累加
        // 流式模式：将最终消息追加到缓冲区
        if (isStreamMode) {
            // 流式模式：追加到缓冲区
            if (hasContent && text != null && !text.isEmpty()) {
                state.getStreamBuffer().getBuffer().append(text);
            }
        } else {
            // 非流式模式：直接使用完成消息的完整文本，清空缓冲区后设置
            // 不需要累加，因为完成消息已经包含完整的文本
            if (hasContent && text != null && !text.isEmpty()) {
                state.getStreamBuffer().getBuffer().setLength(0);
                state.getStreamBuffer().getBuffer().append(text);
            }
        }

        // 获取完整文本和 dialog_id（在重置状态前保存）
        String fullText = state.getStreamBuffer().getBuffer().toString();
        String dialogId = state.getStreamBuffer().getDialogId();
        log.debug("✅ [Complete] Conversation completed, dialog_id={}, totalLength={}", dialogId, fullText.length());

        // 推送完整文本到流队列（表示流结束），同时保存 dialog_id
        if (streamManager.isActive(sessionId)) {
            StreamManager.StreamQueueItem item = new StreamManager.StreamQueueItem();
            item.setValue(fullText);
            item.setDone(true);
            item.setDialogId(dialogId); // 保存 dialog_id
            streamManager.enqueue(sessionId, item);
        }

        // 更新完成时间戳（状态重置前更新）
        state.getSendState().setLastRequestEndTime(System.currentTimeMillis());

        // 清除超时定时器
        if (state.getTimeoutId() != null) {
            state.getTimeoutId().cancel(false);
            state.setTimeoutId(null);
        }

        // Resolve 流完成 Promise
        if (state.getPromiseHandlers().getStreamCompletionResolve() != null) {
            state.getPromiseHandlers().getStreamCompletionResolve().run();
            state.getPromiseHandlers().setStreamCompletionResolve(null);
            state.getPromiseHandlers().setStreamCompletionReject(null);
        }

        // 保存 pendingResolve 引用（在重置状态前保存）
        Consumer<String> pendingResolve = state.getPromiseHandlers().getPendingResolve();

        // 重置状态（重要：必须在触发回调前重置）
        state.getStreamBuffer().setMsgId("");
        state.getSendState().setSending(false);
        state.setSessionState(SessionState.IDLE);
        state.getPromiseHandlers().setPendingResolve(null);
        state.getPromiseHandlers().setPendingReject(null);
        state.getCallbacks().setCurrentOnChunk(null);
        state.getCallbacks().setCurrentOnComplete(null);
        state.getStreamConfig().setCurrentStream(false);
        state.getStreamConfig().setCurrentNodeId(null);
        state.getStreamConfig().setActiveStreamId(null);
        // 清理超时相关状态（移除重复代码）
        state.setTimeout(0);
        state.setRefreshTimeout(null);

        // 触发完成回调
        // 注意：非流式模式下，不应该触发 onComplete 回调，因为结果会通过 Promise 返回
        // 只有流式模式下才触发 onComplete 回调
        if (isStreamMode && currentOnComplete != null) {
            currentOnComplete.accept(fullText);
        }
        // 全局 onComplete 回调只在流式模式下触发，避免非流式模式下重复打印
        if (isStreamMode && callbacks.getOnComplete() != null) {
            callbacks.getOnComplete().accept(fullText);
        }

        // Resolve 主 Promise（非流式模式下）
        // 必须在重置状态后调用，确保状态已清理
        if (pendingResolve != null) {
            pendingResolve.accept(fullText);
        }
    }

    /**
     * 处理特殊响应
     */
    private void handleSpecialResponse(@NonNull WSResponse response, @NonNull String sessionId,
            SessionContext.@Nullable AgentCallbacks callbacks) {
        ResponseIndex indexType = ResponseIndex.fromValue(response.getIndex())
                .orElse(null);
        String typeName = indexType == ResponseIndex.ONLINE_SEARCH ? "online_search" : "images";
        log.debug("Received special response type, sessionId: {}, type: {}",
                CybotStarUtils.formatSessionId(sessionId), typeName);

        // 如果注册了 onMessage 回调，传递特殊内容
        if (callbacks != null && callbacks.getOnMessage() != null) {
            String specialContent;
            Object data = response.getData();
            if (data instanceof String) {
                specialContent = (String) data;
            } else {
                specialContent = JSON.toJSONString(data);
            }
            // 调用回调，只传递 chunk 和 fullText
            callbacks.getOnMessage().accept(specialContent, specialContent);
        }
    }

    /**
     * 处理 Reasoning 响应
     */
    private void handleReasoningResponse(@NonNull WSResponse response, @NonNull String sessionId, @NonNull StreamState state,
            SessionContext.@Nullable AgentCallbacks callbacks) {
        log.debug("Received Reasoning response, sessionId: {}",
                CybotStarUtils.formatSessionId(sessionId));

        // 刷新超时定时器：当收到 reasoning 数据时，刷新超时定时器，避免在 reasoning 输出过程中超时
        refreshTimeoutIfNeeded(state, sessionId);

        // 提取 content 字段
        String content = extractText(response);

        // 如果注册了 onReasoning 回调，优先使用
        if (callbacks != null && callbacks.getOnReasoning() != null) {
            callbacks.getOnReasoning().accept(content);
        } else if (callbacks != null && callbacks.getOnMessage() != null) {
            // 如果没有 onReasoning 回调，使用 onMessage 回调
            callbacks.getOnMessage().accept(content, content);
        }
    }

    /**
     * 处理错误响应
     */
    private void handleErrorResponse(@NonNull WSResponse response, @NonNull String sessionId, @NonNull StreamState state,
            SessionContext.@Nullable AgentCallbacks callbacks) {
        log.debug("Received error response, sessionId: {}, code: {}, message: {}",
                CybotStarUtils.formatSessionId(sessionId), response.getCode(), response.getMessage());

        // 清除超时定时器
        if (state.getTimeoutId() != null) {
            state.getTimeoutId().cancel(false);
            state.setTimeoutId(null);
        }

        // 更新发送状态
        state.getSendState().setSending(false);

        // 创建错误对象
        String errorMessage = response.getMessage() != null ? response.getMessage() : "服务器返回错误码: " + response.getCode();
        RuntimeException error = new RuntimeException(errorMessage);

        // 如果是流式模式，将错误推送到流队列
        if (streamManager.isActive(sessionId)) {
            StreamManager.StreamQueueItem item = new StreamManager.StreamQueueItem();
            item.setValue("");
            item.setDone(true);
            item.setError(error);
            streamManager.enqueue(sessionId, item);
        }

        // 使用 ErrorHandler 统一处理错误
        errorHandler.handleError(response, sessionId, callbacks);

        // 清空 Promise 处理器
        state.getPromiseHandlers().setPendingReject(null);
        state.getPromiseHandlers().setPendingResolve(null);
    }
}
