package com.brgroup.cybotstar.reactive;

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
import com.brgroup.cybotstar.util.ClientUtils;
import com.brgroup.cybotstar.util.Constants;
import com.brgroup.cybotstar.util.FormatUtils;
import com.brgroup.cybotstar.util.payload.PayloadBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
 * Reactive Agent 客户端
 * 基于 Project Reactor 的响应式 Agent 客户端，直接复用底层基础设施
 * 提供 Mono/Flux 终端操作替代阻塞式 send()/stream()
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class ReactiveAgentClient implements DisposableBean {

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
    private final SessionContext.@NonNull AgentCallbacks callbacks = new SessionContext.AgentCallbacks();

    public ReactiveAgentClient(@NonNull AgentConfig config) {
        ClientUtils.validateConfig(config);
        this.config = config;
        this.sessionManager = new SessionContextManager();
        this.streamManager = new StreamManager();
        this.connectionManager = new ConnectionManager(config);
        this.errorHandler = new ErrorHandler();
        this.messageHandler = new MessageHandler(streamManager, errorHandler);
        log.debug("ReactiveAgentClient initialization completed, URL: {}", config.getWebsocket().getUrl());
    }

    // ============================================================================
    // 链式方法（与 AgentClient 完全一致）
    // ============================================================================

    @NonNull
    public ReactiveAgentClient prompt(@NonNull String question) {
        requestBuilderHolder.get().prompt(question);
        return this;
    }

    @NonNull
    public ReactiveAgentClient option(@NonNull ModelOptions modelOptions) {
        requestBuilderHolder.get().option(modelOptions);
        return this;
    }

    @NonNull
    public ReactiveAgentClient session(@NonNull String sessionId) {
        this.threadLocalSessionId.set(sessionId);
        requestBuilderHolder.get().session(sessionId);
        if (!connectionManager.isConnected(sessionId)) {
            connectionManager.connect(sessionId).thenRun(() -> {
                log.debug("Session connection established successfully, sessionId: {}",
                        FormatUtils.formatSessionId(sessionId));
                if (this.callbacks.getOnConnected() != null) {
                    this.callbacks.getOnConnected().run();
                }
            });
        }
        return this;
    }

    @NonNull
    public ReactiveAgentClient onChunk(@NonNull Consumer<String> callback) {
        requestBuilderHolder.get().onChunk(callback);
        return this;
    }

    @NonNull
    public ReactiveAgentClient messages(@NonNull List<MessageParam> messages) {
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }
        requestBuilderHolder.get().setMessageParams(messages);
        return this;
    }

    // ============================================================================
    // 回调注册（与 AgentClient 完全一致）
    // ============================================================================

    public ReactiveAgentClient onMessage(BiConsumer<String, String> callback) {
        this.callbacks.setOnMessage(callback);
        return this;
    }

    public ReactiveAgentClient onComplete(Consumer<String> callback) {
        this.callbacks.setOnComplete(callback);
        return this;
    }

    public ReactiveAgentClient onError(Consumer<Throwable> callback) {
        this.callbacks.setOnError(callback);
        return this;
    }

    public ReactiveAgentClient onConnected(Runnable callback) {
        this.callbacks.setOnConnected(callback);
        return this;
    }

    public ReactiveAgentClient onDisconnected(Runnable callback) {
        this.callbacks.setOnDisconnected(callback);
        return this;
    }

    public ReactiveAgentClient onRawRequest(Consumer<WSPayload> callback) {
        this.callbacks.setOnRawRequest(callback);
        return this;
    }

    public ReactiveAgentClient onRawResponse(Consumer<WSResponse> callback) {
        this.callbacks.setOnRawResponse(callback);
        return this;
    }

    public ReactiveAgentClient onReasoning(Consumer<String> callback) {
        this.callbacks.setOnReasoning(callback);
        return this;
    }

    public ReactiveAgentClient offMessage() { this.callbacks.setOnMessage(null); return this; }
    public ReactiveAgentClient offComplete() { this.callbacks.setOnComplete(null); return this; }
    public ReactiveAgentClient offError() { this.callbacks.setOnError(null); return this; }
    public ReactiveAgentClient offConnected() { this.callbacks.setOnConnected(null); return this; }
    public ReactiveAgentClient offDisconnected() { this.callbacks.setOnDisconnected(null); return this; }
    public ReactiveAgentClient offRawRequest() { this.callbacks.setOnRawRequest(null); return this; }
    public ReactiveAgentClient offRawResponse() { this.callbacks.setOnRawResponse(null); return this; }
    public ReactiveAgentClient offReasoning() { this.callbacks.setOnReasoning(null); return this; }

    // ============================================================================
    // Reactive 终端操作
    // ============================================================================

    /**
     * 非流式发送，返回 Mono&lt;String&gt;（完整响应文本）
     */
    @NonNull
    public Mono<String> send() {
        RequestBuilder requestBuilder = requestBuilderHolder.get();
        String threadSession = threadLocalSessionId.get();
        String effectiveDefaultSession = threadSession != null ? threadSession : Constants.DEFAULT_SESSION_ID;
        RequestBuilder.RequestConfig requestConfig = requestBuilder.buildRequestConfig(effectiveDefaultSession);
        requestBuilder.reset();
        ExtendedSendOptions mergedOptions = mergeSessionOptions(requestConfig.sessionId(), requestConfig.options());

        // 清理 ThreadLocal
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
     */
    @NonNull
    public Flux<String> stream() {
        RequestBuilder requestBuilder = requestBuilderHolder.get();
        String threadSession = threadLocalSessionId.get();
        String effectiveDefaultSession = threadSession != null ? threadSession : Constants.DEFAULT_SESSION_ID;
        RequestBuilder.RequestConfig requestConfig = requestBuilder.buildRequestConfig(effectiveDefaultSession);
        requestBuilder.reset();
        ExtendedSendOptions mergedOptions = mergeSessionOptions(requestConfig.sessionId(), requestConfig.options());
        String sessionId = requestConfig.sessionId();
        Consumer<String> onChunk = requestConfig.onChunk();

        // 清理 ThreadLocal
        requestBuilderHolder.remove();
        threadLocalSessionId.remove();

        return Flux.<String>create(sink -> {
            // 获取流式状态
            StreamState state = sessionManager.getStreamState(sessionId);

            // 设置流完成 Promise
            CompletableFuture<Void> completionFuture = new CompletableFuture<>();
            state.getPromiseHandlers().setStreamCompletionResolve(() -> completionFuture.complete(null));
            state.getPromiseHandlers().setStreamCompletionReject(completionFuture::completeExceptionally);

            // 使用 onChunk 回调将数据推送到 FluxSink
            Consumer<String> fluxOnChunk = chunk -> {
                // 先触发用户注册的 onChunk 回调
                if (onChunk != null) {
                    onChunk.accept(chunk);
                }
                // 推送到 Flux
                sink.next(chunk);
            };

            // 发送请求
            CompletableFuture<String> sendFuture = sendInternal(
                    requestConfig.question(), true, fluxOnChunk, null,
                    sessionId, mergedOptions);

            sendFuture.whenComplete((result, sendError) -> {
                if (sendError != null) {
                    sink.error(sendError);
                }
            });

            // 等待流完成
            completionFuture.whenComplete((v, completionError) -> {
                if (completionError != null) {
                    sink.error(completionError);
                } else {
                    sink.complete();
                }
            });
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ============================================================================
    // 内部发送实现（复用 AgentClient 逻辑）
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
                    : Constants.DEFAULT_RESPONSE_TIMEOUT;
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
                                FormatUtils.formatSessionId(sessionId), timeout);
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
        final String effectiveSessionId = (sessionId == null) ? Constants.DEFAULT_SESSION_ID : sessionId;
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
        if (response == null) { log.warn("Received null response, sessionId: {}", FormatUtils.formatSessionId(sessionId)); return; }
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
            log.error("Failed to process WebSocket message, sessionId: {}", FormatUtils.formatSessionId(sessionId), e);
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

    public SessionContext getSessionContext(String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    public void disconnect() { disconnect(""); }

    public void disconnect(String sessionId) {
        log.debug("Disconnecting WebSocket connection, sessionId: {}", FormatUtils.formatSessionId(sessionId));
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
            log.debug("Error cleaning up session timers, sessionId: {}", FormatUtils.formatSessionId(sessionId), e);
        }
    }

    private void shutdownScheduler() {
        if (scheduler.isShutdown()) return;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Scheduler did not terminate gracefully, forcing shutdown");
                scheduler.shutdownNow();
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("Scheduler did not terminate after forced shutdown");
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
        return mergeOptionsNonNull(merged, options);
    }

    private ExtendedSendOptions mergeOptionsNonNull(@Nullable ExtendedSendOptions base, @Nullable ExtendedSendOptions override) {
        if (base == null) base = new ExtendedSendOptions();
        if (override == null) return base;
        ExtendedSendOptions merged = ClientUtils.mergeOptions(base, new ExtendedSendOptions());
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
