package com.brgroup.cybotstar.agent;

import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.agent.model.MessageParam;
import com.brgroup.cybotstar.connection.ConnectionManager;
import com.brgroup.cybotstar.connection.WebSocketConnection;
import com.brgroup.cybotstar.agent.exception.AgentException;
import com.brgroup.cybotstar.handler.ErrorHandler;
import com.brgroup.cybotstar.handler.MessageHandler;
import com.brgroup.cybotstar.agent.session.SessionContext;
import com.brgroup.cybotstar.agent.session.SessionContextManager;
import com.brgroup.cybotstar.stream.StreamManager;
import com.brgroup.cybotstar.stream.StreamState;
import com.brgroup.cybotstar.agent.internal.RequestBuilder;
import com.brgroup.cybotstar.agent.model.ExtendedSendOptions;
import com.brgroup.cybotstar.agent.model.ModelOptions;
import com.brgroup.cybotstar.model.common.ResponseType;
import com.brgroup.cybotstar.model.common.SessionState;
import com.brgroup.cybotstar.model.ws.WSPayload;
import com.brgroup.cybotstar.model.ws.WSResponse;
import com.brgroup.cybotstar.util.CybotStarConstants;
import com.brgroup.cybotstar.util.CybotStarUtils;
import com.brgroup.cybotstar.util.payload.PayloadBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/**
 * Agent 客户端
 * 基于 Project Reactor 的响应式 Agent 客户端
 * 提供 Mono/Flux 终端操作
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class AgentClient implements DisposableBean {

    @NonNull
    private final AgentConfig config;
    @NonNull
    private final SessionContextManager sessionManager;
    @NonNull
    private final ConnectionManager connectionManager;
    @NonNull
    private final StreamManager streamManager;
    @NonNull
    private final MessageHandler messageHandler;
    @NonNull
    private final ErrorHandler errorHandler;
    @NonNull
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    // 使用 ThreadLocal 支持并发调用
    private final ThreadLocal<RequestBuilder> requestBuilderHolder = ThreadLocal.withInitial(RequestBuilder::new);
    private final ThreadLocal<String> threadLocalSessionId = new ThreadLocal<>();
    // 全局默认 session ID（贯穿客户端生命周期）
    @Nullable
    private volatile String defaultSessionId;
    private final SessionContext.@NonNull AgentCallbacks callbacks = new SessionContext.AgentCallbacks();

    public AgentClient(@NonNull AgentConfig config) {
        CybotStarUtils.validateConfig(config);
        this.config = config;
        this.sessionManager = new SessionContextManager();
        this.streamManager = new StreamManager();
        this.connectionManager = new ConnectionManager(config);
        this.errorHandler = new ErrorHandler();
        this.messageHandler = new MessageHandler(streamManager, errorHandler);
        log.debug("AgentClient initialization completed, URL: {}", config.getWebsocket().getUrl());
    }

    // ============================================================================
    // 链式方法
    // ============================================================================

    @NonNull
    public AgentClient prompt(@NonNull String question) {
        requestBuilderHolder.get().prompt(question);
        return this;
    }

    @NonNull
    public AgentClient option(@NonNull ModelOptions modelOptions) {
        requestBuilderHolder.get().option(modelOptions);
        return this;
    }

    @NonNull
    public AgentClient session(@NonNull String sessionId) {
        this.threadLocalSessionId.set(sessionId);
        // 同时更新全局默认 session
        this.defaultSessionId = sessionId;
        requestBuilderHolder.get().session(sessionId);
        if (!connectionManager.isConnected(sessionId)) {
            connectionManager.connect(sessionId).thenRun(() -> {
                log.debug("Session connection established successfully, sessionId: {}",
                        CybotStarUtils.formatSessionId(sessionId));
                if (this.callbacks.getOnConnected() != null) {
                    this.callbacks.getOnConnected().run();
                }
            });
        }
        return this;
    }

    /**
     * 获取当前有效的 session ID
     * 优先级：ThreadLocal > 全局默认 > 常量默认值
     */
    @NonNull
    private String getEffectiveSessionId() {
        String threadSession = threadLocalSessionId.get();
        if (threadSession != null) {
            return threadSession;
        }
        if (defaultSessionId != null) {
            return defaultSessionId;
        }
        return CybotStarConstants.DEFAULT_SESSION_ID;
    }


    @NonNull
    public AgentClient messages(@NonNull List<MessageParam> messages) {
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }
        requestBuilderHolder.get().setMessageParams(messages);
        return this;
    }

    // ============================================================================
    // 回调注册
    // ============================================================================

    public AgentClient onMessage(BiConsumer<String, String> callback) {
        this.callbacks.setOnMessage(callback);
        return this;
    }

    public AgentClient onComplete(Consumer<String> callback) {
        this.callbacks.setOnComplete(callback);
        return this;
    }

    public AgentClient onError(Consumer<Throwable> callback) {
        this.callbacks.setOnError(callback);
        return this;
    }

    public AgentClient onConnected(Runnable callback) {
        this.callbacks.setOnConnected(callback);
        return this;
    }

    public AgentClient onDisconnected(Runnable callback) {
        this.callbacks.setOnDisconnected(callback);
        return this;
    }

    public AgentClient onRawRequest(Consumer<WSPayload> callback) {
        this.callbacks.setOnRawRequest(callback);
        return this;
    }

    public AgentClient onRawResponse(Consumer<WSResponse> callback) {
        this.callbacks.setOnRawResponse(callback);
        return this;
    }

    public AgentClient onReasoning(Consumer<String> callback) {
        this.callbacks.setOnReasoning(callback);
        return this;
    }

    public AgentClient offMessage() { this.callbacks.setOnMessage(null); return this; }
    public AgentClient offComplete() { this.callbacks.setOnComplete(null); return this; }
    public AgentClient offError() { this.callbacks.setOnError(null); return this; }
    public AgentClient offConnected() { this.callbacks.setOnConnected(null); return this; }
    public AgentClient offDisconnected() { this.callbacks.setOnDisconnected(null); return this; }
    public AgentClient offRawRequest() { this.callbacks.setOnRawRequest(null); return this; }
    public AgentClient offRawResponse() { this.callbacks.setOnRawResponse(null); return this; }
    public AgentClient offReasoning() { this.callbacks.setOnReasoning(null); return this; }

    // ============================================================================
    // Reactive 终端操作
    // ============================================================================

    /**
     * 非流式发送，返回 Mono&lt;String&gt;（完整响应文本）
     */
    @NonNull
    public Mono<String> send() {
        RequestBuilder requestBuilder = requestBuilderHolder.get();
        String effectiveDefaultSession = getEffectiveSessionId();
        RequestBuilder.RequestConfig requestConfig = requestBuilder.buildRequestConfig(effectiveDefaultSession);
        requestBuilder.reset();
        ExtendedSendOptions mergedOptions = mergeSessionOptions(requestConfig.sessionId(), requestConfig.options());

        // 清理 ThreadLocal（但保留全局默认 session）
        requestBuilderHolder.remove();
        threadLocalSessionId.remove();

        return Mono.<String>create(sink -> {
            CompletableFuture<String> future = sendInternal(
                    requestConfig.question(), false, null, null,
                    requestConfig.sessionId(), mergedOptions);
            future.whenComplete((result, error) -> {
                if (error != null) {
                    sink.error(error);
                } else {
                    sink.success(result);
                }
            });
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 流式发送，返回 Flux&lt;String&gt;（每个元素是一个 chunk）
     * 使用 Reactor 操作符处理流：
     * - doOnNext() - 处理每个 chunk
     * - doOnComplete() - 流完成时回调
     * - doOnError() - 错误处理
     *
     * 完全基于 Reactor 的响应式实现，使用 Sinks.many() 作为生产者-消费者桥梁
     */
    @NonNull
    public Flux<String> stream() {
        RequestBuilder requestBuilder = requestBuilderHolder.get();
        String effectiveDefaultSession = getEffectiveSessionId();
        RequestBuilder.RequestConfig requestConfig = requestBuilder.buildRequestConfig(effectiveDefaultSession);
        requestBuilder.reset();
        ExtendedSendOptions mergedOptions = mergeSessionOptions(requestConfig.sessionId(), requestConfig.options());
        String sessionId = requestConfig.sessionId();

        // 清理 ThreadLocal（但保留全局默认 session）
        requestBuilderHolder.remove();
        threadLocalSessionId.remove();

        // 创建 Reactor Sinks.Many 作为生产者-消费者桥梁
        // 使用 unicast() 确保单一订阅者，避免多播导致的延迟
        // 使用 onBackpressureBuffer() 处理背压
        reactor.core.publisher.Sinks.Many<String> sink = reactor.core.publisher.Sinks.many()
                .unicast()
                .onBackpressureBuffer();

        // 获取流式状态
        StreamState state = sessionManager.getStreamState(sessionId);

        // 设置 chunk 回调，将数据推送到 Reactor Sink
        Consumer<String> fluxOnChunk = chunk -> {
            reactor.core.publisher.Sinks.EmitResult result = sink.tryEmitNext(chunk);
            if (result.isFailure()) {
                log.warn("Failed to emit chunk to sink, result: {}, sessionId: {}", result, sessionId);
                // 如果失败，尝试使用阻塞方式发送
                if (result == reactor.core.publisher.Sinks.EmitResult.FAIL_NON_SERIALIZED) {
                    sink.emitNext(chunk, reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST);
                }
            }
        };

        // 设置流完成 Promise
        CompletableFuture<Void> completionFuture = new CompletableFuture<>();
        state.getPromiseHandlers().setStreamCompletionResolve(() -> {
            completionFuture.complete(null);
            // 完成 Reactor Sink
            reactor.core.publisher.Sinks.EmitResult result = sink.tryEmitComplete();
            if (result.isFailure()) {
                log.warn("Failed to complete sink, result: {}, sessionId: {}", result, sessionId);
                sink.emitComplete(reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST);
            }
        });
        state.getPromiseHandlers().setStreamCompletionReject(error -> {
            completionFuture.completeExceptionally(error);
            // 发送错误到 Reactor Sink
            reactor.core.publisher.Sinks.EmitResult result = sink.tryEmitError(error);
            if (result.isFailure()) {
                log.warn("Failed to emit error to sink, result: {}, sessionId: {}", result, sessionId);
                sink.emitError(error, reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST);
            }
        });

        // 异步发送请求
        CompletableFuture.runAsync(() -> {
            try {
                sendInternal(
                        requestConfig.question(), true, fluxOnChunk, null,
                        sessionId, mergedOptions);
            } catch (Exception e) {
                reactor.core.publisher.Sinks.EmitResult result = sink.tryEmitError(e);
                if (result.isFailure()) {
                    log.warn("Failed to emit error to sink, result: {}, sessionId: {}", result, sessionId);
                    sink.emitError(e, reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST);
                }
            }
        });

        // 返回 Flux，订阅时自动开始消费
        // 不使用 subscribeOn，让订阅在调用者线程中进行，确保实时性
        return sink.asFlux()
                .doOnCancel(() -> {
                    log.debug("Flux subscription cancelled, cleaning up stream resources, sessionId: {}", sessionId);
                    streamManager.cleanup(sessionId);
                });
    }

    // ============================================================================
    // 内部发送实现
    // ============================================================================

    private CompletableFuture<String> sendInternal(
            @NonNull String question, boolean stream,
            @Nullable Consumer<String> onChunk, @Nullable Consumer<String> onComplete,
            @NonNull String sessionId, @Nullable ExtendedSendOptions options) {

        CompletableFuture<String> future = new CompletableFuture<>();
        WebSocketConnection connection = getConnection(sessionId);

        if (!connection.isConnected()) {
            connectionManager.connect(sessionId).thenRun(() ->
                    executeSend(future, question, stream, onChunk, onComplete, sessionId, options));
        } else {
            executeSend(future, question, stream, onChunk, onComplete, sessionId, options);
        }
        return future;
    }

    private void executeSend(
            @NonNull CompletableFuture<String> future,
            @NonNull String question, boolean stream,
            @Nullable Consumer<String> onChunk, @Nullable Consumer<String> onComplete,
            @NonNull String sessionId, @Nullable ExtendedSendOptions options) {

        StreamState state = sessionManager.getStreamState(sessionId);
        try {
            synchronized (state.getSendState()) {
                if (state.getSendState().isSending()) {
                    future.completeExceptionally(new IllegalStateException("上一个请求正在处理中，请等待完成"));
                    return;
                }
                state.getSendState().setSending(true);
                state.setSessionState(SessionState.CHATTING);
                state.getStreamBuffer().getBuffer().setLength(0);
                state.getStreamBuffer().setMsgId("");
                state.getStreamBuffer().setDialogId(null);
                state.getStreamBuffer().setQuestion(question);
                state.getStreamConfig().setCurrentNodeId(null);
                state.getStreamConfig().setCurrentStream(stream);
                state.getCallbacks().setCurrentOnChunk(onChunk);
                state.getCallbacks().setCurrentOnComplete(onComplete);
            }

            // 清理旧超时定时器
            ScheduledFuture<?> oldTimeoutId = state.getTimeoutId();
            if (oldTimeoutId != null) {
                try { if (!oldTimeoutId.isDone()) oldTimeoutId.cancel(false); }
                catch (Exception e) { log.debug("Error canceling old timeout timer", e); }
                finally { state.setTimeoutId(null); }
            }
            state.setRefreshTimeout(null);

            // 准备流
            if (stream) {
                streamManager.prepareStream(sessionId);
                state.getStreamConfig().setActiveStreamId(sessionId);
            }

            // 构建载荷
            WSPayload payload = PayloadBuilder.buildPayload(config, question, sessionId, options);

            // 触发原始请求回调
            SessionContext.AgentCallbacks cbs = getCallbacks(sessionId);
            if (cbs.getOnRawRequest() != null) {
                cbs.getOnRawRequest().accept(payload);
            }

            // 设置 Promise 处理器
            state.getPromiseHandlers().setPendingResolve(result ->
                    future.complete(Objects.requireNonNullElse(result, "sent")));
            state.getPromiseHandlers().setPendingReject(future::completeExceptionally);

            // 超时处理
            long timeout = config.getWebsocket().getTimeout() != null
                    ? config.getWebsocket().getTimeout()
                    : CybotStarConstants.DEFAULT_RESPONSE_TIMEOUT;
            state.setTimeout(timeout);

            Runnable setupTimeout = () -> {
                ScheduledFuture<?> existingTimeoutId = state.getTimeoutId();
                if (existingTimeoutId != null) {
                    try { if (!existingTimeoutId.isDone()) existingTimeoutId.cancel(false); }
                    catch (Exception e) { log.debug("Error canceling old timeout timer in setupTimeout", e); }
                }
                ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
                    if (state.getSendState().isSending()) {
                        state.getSendState().setSending(false);
                        state.setTimeoutId(null);
                        AgentException timeoutError = AgentException.responseTimeout(timeout);
                        if (state.getStreamConfig().getActiveStreamId() != null) {
                            StreamManager.StreamQueueItem item = new StreamManager.StreamQueueItem();
                            item.setValue(""); item.setDone(true); item.setError(timeoutError);
                            streamManager.enqueue(sessionId, item);
                        }
                        SessionContext.AgentCallbacks stateCbs = getCallbacks(sessionId);
                        if (stateCbs.getOnError() != null) stateCbs.getOnError().accept(timeoutError);
                        log.warn("Response timeout, sessionId: {}, timeout: {}ms",
                                 CybotStarUtils.formatSessionId(sessionId), timeout);
                        if (state.getPromiseHandlers().getStreamCompletionReject() != null) {
                            state.getPromiseHandlers().getStreamCompletionReject().accept(timeoutError);
                            state.getPromiseHandlers().setStreamCompletionResolve(null);
                            state.getPromiseHandlers().setStreamCompletionReject(null);
                        }
                        state.getPromiseHandlers().setPendingResolve(null);
                        state.getPromiseHandlers().setPendingReject(null);
                    }
                }, timeout, TimeUnit.MILLISECONDS);
                state.setTimeoutId(timeoutFuture);
            };

            setupTimeout.run();
            state.setRefreshTimeout(setupTimeout);

            // 发送消息
            WebSocketConnection connection = getConnection(sessionId);
            connection.send(payload);

            if (stream) {
                future.complete("sent");
            }
        } catch (Exception e) {
            ScheduledFuture<?> timeoutId = state.getTimeoutId();
            if (timeoutId != null) {
                try { if (!timeoutId.isDone()) timeoutId.cancel(false); }
                catch (Exception ce) { log.debug("Error canceling timeout timer during exception", ce); }
                finally { state.setTimeoutId(null); }
            }
            state.setRefreshTimeout(null);
            future.completeExceptionally(e);
        }
    }

    // ============================================================================
    // 连接管理
    // ============================================================================

    @NonNull
    private WebSocketConnection getConnection(@Nullable String sessionId) {
        final String effectiveSessionId = (sessionId == null) ? CybotStarConstants.DEFAULT_SESSION_ID : sessionId;
        WebSocketConnection connection = connectionManager.getConnection(effectiveSessionId);
        SessionContext context = sessionManager.getSession(effectiveSessionId);

        if (context.getMessageHandler() == null) {
            synchronized (context) {
                if (context.getMessageHandler() == null) {
                    WebSocketConnection.WSMessageHandler handler = response ->
                            handleWebSocketMessage(response, effectiveSessionId);
                    connection.onMessage(handler);
                    context.setMessageHandler(handler);
                }
            }
        }
        context.setConnection(connection);
        return connection;
    }

    private void handleWebSocketMessage(@Nullable WSResponse response, @Nullable String sessionId) {
        if (response == null) { log.warn("Received null response, sessionId: {}", CybotStarUtils.formatSessionId(sessionId)); return; }
        if (sessionId == null) { log.warn("SessionId is null when handling WebSocket message"); return; }

        StreamState state = sessionManager.getStreamState(sessionId);
        SessionContext.AgentCallbacks cbs = getCallbacks(sessionId);

        try {
            MessageHandler.MessageHandleResult result = messageHandler.handle(response, sessionId, state, cbs);
            if (!result.isShouldContinue()) return;

            String dialogId = response.getDialogId();
            if (dialogId != null && state.getStreamBuffer().getDialogId() == null) {
                state.getStreamBuffer().setDialogId(dialogId);
            }

            boolean isStreamMode = state.getStreamConfig().isCurrentStream();
            if (isStreamMode && !result.isFinal() && result.isHasContent()
                    && !ResponseType.isType(response.getType(), ResponseType.FLOW)) {
                messageHandler.appendStreamContent(result.getText(), sessionId, state, cbs);
                return;
            }

            if (result.isFinal()) {
                String question = state.getStreamBuffer().getQuestion();
                String answer = state.getStreamBuffer().getBuffer().toString();
                String dialogIdFromBuffer = state.getStreamBuffer().getDialogId();
                if (question != null && !question.isEmpty() && !answer.isEmpty()) {
                    SessionContext context = sessionManager.getSession(sessionId);
                    context.addHistory(MessageParam.builder().role("user").content(question).dialogId(dialogIdFromBuffer).build());
                    context.addHistory(MessageParam.builder().role("assistant").content(answer).dialogId(dialogIdFromBuffer).build());
                }
                messageHandler.handleMessageComplete(result.getText(), result.isHasContent(), sessionId, state, cbs);
            }
        } catch (Exception e) {
            try { if (state.getTimeoutId() != null) { state.getTimeoutId().cancel(false); state.setTimeoutId(null); } state.setRefreshTimeout(null); } catch (Exception ce) { log.debug("Error cleaning up timeout timer", ce); }
            try { state.getSendState().setSending(false); } catch (Exception se) { log.debug("Error updating send state", se); }
            AgentException wrappedError = AgentException.wrap(e);
            try { if (streamManager.isActive(sessionId)) { StreamManager.StreamQueueItem item = new StreamManager.StreamQueueItem(); item.setValue(""); item.setDone(true); item.setError(wrappedError); streamManager.enqueue(sessionId, item); } } catch (Exception se) { log.debug("Error enqueueing error to stream", se); }
            try {
                if (state.getPromiseHandlers().getPendingReject() != null) { state.getPromiseHandlers().getPendingReject().accept(wrappedError); state.getPromiseHandlers().setPendingReject(null); state.getPromiseHandlers().setPendingResolve(null); }
                if (state.getPromiseHandlers().getStreamCompletionReject() != null) { state.getPromiseHandlers().getStreamCompletionReject().accept(wrappedError); state.getPromiseHandlers().setStreamCompletionResolve(null); state.getPromiseHandlers().setStreamCompletionReject(null); }
            } catch (Exception pe) { log.debug("Error handling promise rejection", pe); }
            log.error("Failed to process WebSocket message, sessionId: {}", CybotStarUtils.formatSessionId(sessionId), e);
            try { errorHandler.handleException(wrappedError, sessionId, cbs); } catch (Exception he) { log.error("Error in error handler", he); }
        }
    }

    // ============================================================================
    // 透传方法 & 资源清理
    // ============================================================================

    private SessionContext.@NonNull AgentCallbacks getCallbacks(@Nullable String sessionId) {
        return this.callbacks;
    }

    @NonNull
    public AgentConfig getConfig() { return config; }

    /**
     * 获取当前会话状态
     */
    public SessionState getSessionState(String sessionId) {
        return sessionManager.getSessionState(sessionId);
    }

    public SessionContext getSessionContext(String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    public void disconnect() { disconnect(""); }

    public void disconnect(String sessionId) {
        log.debug("Disconnecting WebSocket connection, sessionId: {}", CybotStarUtils.formatSessionId(sessionId));
        sessionManager.resetAllState(sessionId);
        sessionManager.remove(sessionId);
        connectionManager.disconnect(sessionId);
    }

    public void close() {
        try {
            for (String sid : sessionManager.getAllSessionIds()) { cleanupSessionTimers(sid); }
            for (String sid : sessionManager.getAllSessionIds()) { disconnect(sid); }
        } catch (Exception e) {
            log.error("Error during client close", e);
        } finally {
            shutdownScheduler();
        }
    }

    private void cleanupSessionTimers(String sessionId) {
        try {
            StreamState state = sessionManager.getStreamState(sessionId);
            ScheduledFuture<?> timeoutId = state.getTimeoutId();
            if (timeoutId != null && !timeoutId.isDone()) timeoutId.cancel(false);
            state.setTimeoutId(null);
            state.setRefreshTimeout(null);
        } catch (Exception e) {
            log.debug("Error cleaning up session timers, sessionId: {}", CybotStarUtils.formatSessionId(sessionId), e);
        }
    }

    private void shutdownScheduler() {
        if (scheduler.isShutdown()) return;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
//                log.warn("Scheduler did not terminate gracefully, forcing shutdown, sessionId: {}", CybotStarUtils.formatSessionId(sessionId));
                scheduler.shutdownNow();
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
//                    log.error("Scheduler did not terminate after forced shutdown, sessionId: {}", CybotStarUtils.formatSessionId(sessionManager.getCurrentSessionId()));
                }
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Unexpected error during scheduler shutdown", e);
            scheduler.shutdownNow();
        }
    }

    @Override
    public void destroy() { close(); }

    // ============================================================================
    // 选项合并
    // ============================================================================

    private ExtendedSendOptions mergeSessionOptions(@Nullable String sessionId, @Nullable ExtendedSendOptions options) {
        if (options == null) options = new ExtendedSendOptions();
        if (sessionId == null) return options;
        SessionContext context = sessionManager.getSession(sessionId);
        ExtendedSendOptions sessionOptions = context.getConfig();
        if (sessionOptions == null) return options;
        ExtendedSendOptions merged = mergeOptionsNonNull(new ExtendedSendOptions(), sessionOptions);
        ExtendedSendOptions result = mergeOptionsNonNull(merged, options);

        // 合并历史消息到 messageParams
        List<MessageParam> historyMessages = context.getHistoryMessages();
        if (historyMessages != null && !historyMessages.isEmpty()) {
            List<MessageParam> existingParams = result.getMessageParams();
            List<MessageParam> finalParams = new ArrayList<>();

            // 先添加历史消息
            finalParams.addAll(historyMessages);

            // 再添加现有的 messageParams（如果有）
            if (existingParams != null && !existingParams.isEmpty()) {
                finalParams.addAll(existingParams);
            }

            result.setMessageParams(finalParams);
        }

        return result;
    }

    private ExtendedSendOptions mergeOptionsNonNull(@Nullable ExtendedSendOptions base, @Nullable ExtendedSendOptions override) {
        if (base == null) base = new ExtendedSendOptions();
        if (override == null) return base;
        ExtendedSendOptions merged = CybotStarUtils.mergeOptions(base, new ExtendedSendOptions());
        if (override.getExtraHeader() != null) merged.setExtraHeader(override.getExtraHeader());
        if (override.getExtraBody() != null) merged.setExtraBody(override.getExtraBody());
        if (override.getMessageParams() != null) merged.setMessageParams(override.getMessageParams());
        if (override.getChatHistory() != null) merged.setChatHistory(override.getChatHistory());
        if (override.getTipMessageExtra() != null) merged.setTipMessageExtra(override.getTipMessageExtra());
        if (override.getTipMessageParams() != null) merged.setTipMessageParams(override.getTipMessageParams());
        if (override.getModelOptions() != null) merged.setModelOptions(override.getModelOptions());
        return merged;
    }
}
