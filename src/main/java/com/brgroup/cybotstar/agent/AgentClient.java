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
import com.brgroup.cybotstar.util.ClientUtils;
import com.brgroup.cybotstar.util.Constants;
import com.brgroup.cybotstar.util.FormatUtils;
import com.brgroup.cybotstar.util.payload.PayloadBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;

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
 * Agent 客户端类
 * 这是 SDK 的核心类，负责协调各个管理器完成与智能体服务的通信
 *
 * 实现 DisposableBean 接口，支持 Spring Boot 环境下的自动资源清理
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

    // 定时任务调度器，用于超时定时器
    @NonNull
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 消息 ID 计数器

    // 请求构建器
    @NonNull
    private final RequestBuilder requestBuilder = new RequestBuilder();

    // Client 级别回调（所有 session 共享）
    private final SessionContext.@NonNull AgentCallbacks callbacks = new SessionContext.AgentCallbacks();

    // 默认会话ID（调用 session() 后设置，后续请求自动使用）
    private String defaultSessionId;

    /**
     * AgentClient 构造方法
     *
     * @param config Agent 配置（不能为 null）
     */
    public AgentClient(@NonNull AgentConfig config) {
        ClientUtils.validateConfig(config);
        this.config = config;

        // 初始化各个管理器
        this.sessionManager = new SessionContextManager();
        this.streamManager = new StreamManager();
        this.connectionManager = new ConnectionManager(config);
        this.errorHandler = new ErrorHandler();
        this.messageHandler = new MessageHandler(streamManager, errorHandler);

        log.debug("AgentClient initialization completed, URL: {}", config.getWebsocket().getUrl());
    }

    /**
     * 设置用户问题（链式调用）
     *
     * @param question 用户问题（不能为 null）
     * @return this，支持链式调用
     */
    @NonNull
    public AgentClient prompt(@NonNull String question) {
        requestBuilder.prompt(question);
        return this;
    }

    /**
     * 设置模型参数（链式调用）
     * 
     * @param modelOptions 模型参数配置（不能为 null）
     * @return this，支持链式调用
     */
    @NonNull
    public AgentClient option(@NonNull ModelOptions modelOptions) {
        requestBuilder.option(modelOptions);
        return this;
    }

    /**
     * 设置会话 ID（链式调用）
     * 
     * 调用此方法后，会设置默认会话ID。后续的 send() 和 stream() 方法如果没有显式指定 sessionId，
     * 会自动使用此会话ID。这简化了同一会话多次请求的使用方式。
     * 
     * 此方法会自动检查并建立连接。如果该会话的连接未建立，会在后台异步启动连接。
     * 连接不会阻塞链式调用，如果连接未完成，后续的 send() 或 stream() 调用会等待连接完成。
     * 
     * 注意：如果在链式调用中再次调用 .session(sessionId)，会覆盖本次设置的默认会话ID。
     * 
     * @param sessionId 会话ID（不能为 null）
     * @return this，支持链式调用
     * 
     * @example
     *          ```java
     *          // 设置默认会话后，后续请求自动使用，连接会自动建立
     *          client.session("session-123");
     *          client.prompt("你好").send(); // 自动使用 session-123，连接已自动建立
     *          client.prompt("继续").send(); // 自动使用 session-123
     * 
     *          // 链式调用中显式指定会覆盖默认值
     *          client.session("session-123")
     *          .prompt("你好")
     *          .session("session-456") // 覆盖为 session-456，连接会自动建立
     *          .send(); // 使用 session-456
     *          ```
     */
    @NonNull
    public AgentClient session(@NonNull String sessionId) {
        // 设置默认会话ID（用于后续请求）
        this.defaultSessionId = sessionId;
        // 同时设置到请求构建器（用于当前链式调用）
        requestBuilder.session(sessionId);

        // 自动建立连接（如果未连接）
        if (!connectionManager.isConnected(sessionId)) {
            // 异步启动连接，不阻塞链式调用
            connectionManager.connect(sessionId).thenRun(() -> {
                log.debug("Session connection established successfully, sessionId: {}",
                        FormatUtils.formatSessionId(sessionId));
                // 触发连接成功回调
                if (this.callbacks.getOnConnected() != null) {
                    this.callbacks.getOnConnected().run();
                }
            });
        }

        return this;
    }

    /**
     * 设置流式输出回调（链式调用）
     *
     * @param callback 流式输出回调（不能为 null）
     * @return this，支持链式调用
     */
    @NonNull
    public AgentClient onChunk(@NonNull Consumer<String> callback) {
        requestBuilder.onChunk(callback);
        return this;
    }

    /**
     * 设置消息参数（链式调用）
     * 这是 messageParams 的便捷方法，提供更简洁的 API
     * 替代了原来的 role() 和 history() 方法
     * 
     * @param messages 消息参数列表，格式同 OpenAI 接口的 messages 字段（不能为 null 或空）
     * @return this，支持链式调用
     */
    @NonNull
    public AgentClient messages(@NonNull List<MessageParam> messages) {
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }
        requestBuilder.setMessageParams(messages);
        return this;
    }

    /**
     * 注册消息回调
     * 
     * @param callback 消息回调，接收消息ID和消息内容
     * @return this，支持链式调用
     */
    public AgentClient onMessage(BiConsumer<String, String> callback) {
        this.callbacks.setOnMessage(callback);
        return this;
    }

    /**
     * 注册完成回调
     * 
     * @param callback 完成回调，接收完整的响应内容
     * @return this，支持链式调用
     */
    public AgentClient onComplete(Consumer<String> callback) {
        this.callbacks.setOnComplete(callback);
        return this;
    }

    /**
     * 注册错误回调
     * 
     * @param callback 错误回调，接收异常对象
     * @return this，支持链式调用
     */
    public AgentClient onError(Consumer<Throwable> callback) {
        this.callbacks.setOnError(callback);
        return this;
    }

    /**
     * 注册连接成功回调
     * 
     * @param callback 连接成功回调
     * @return this，支持链式调用
     */
    public AgentClient onConnected(Runnable callback) {
        this.callbacks.setOnConnected(callback);
        return this;
    }

    /**
     * 注册断开连接回调
     * 
     * @param callback 断开连接回调
     * @return this，支持链式调用
     */
    public AgentClient onDisconnected(Runnable callback) {
        this.callbacks.setOnDisconnected(callback);
        return this;
    }

    /**
     * 注册原始请求回调
     * 
     * @param callback 原始请求回调，接收 WebSocket 请求载荷（用于调试）
     * @return this，支持链式调用
     */
    public AgentClient onRawRequest(Consumer<WSPayload> callback) {
        this.callbacks.setOnRawRequest(callback);
        return this;
    }

    /**
     * 注册原始响应回调
     * 
     * @param callback 原始响应回调，接收 WebSocket 响应对象（用于调试）
     * @return this，支持链式调用
     */
    public AgentClient onRawResponse(Consumer<WSResponse> callback) {
        this.callbacks.setOnRawResponse(callback);
        return this;
    }

    /**
     * 注册 Reasoning 回调
     * 
     * @param callback Reasoning 回调，接收思考内容
     * @return this，支持链式调用
     */
    public AgentClient onReasoning(Consumer<String> callback) {
        this.callbacks.setOnReasoning(callback);
        return this;
    }

    /**
     * 移除消息回调
     * 
     * @return this，支持链式调用
     */
    public AgentClient offMessage() {
        this.callbacks.setOnMessage(null);
        return this;
    }

    /**
     * 移除完成回调
     * 
     * @return this，支持链式调用
     */
    public AgentClient offComplete() {
        this.callbacks.setOnComplete(null);
        return this;
    }

    /**
     * 移除错误回调
     * 
     * @return this，支持链式调用
     */
    public AgentClient offError() {
        this.callbacks.setOnError(null);
        return this;
    }

    /**
     * 移除连接成功回调
     * 
     * @return this，支持链式调用
     */
    public AgentClient offConnected() {
        this.callbacks.setOnConnected(null);
        return this;
    }

    /**
     * 移除断开连接回调
     * 
     * @return this，支持链式调用
     */
    public AgentClient offDisconnected() {
        this.callbacks.setOnDisconnected(null);
        return this;
    }

    /**
     * 移除原始请求回调
     * 
     * @return this，支持链式调用
     */
    public AgentClient offRawRequest() {
        this.callbacks.setOnRawRequest(null);
        return this;
    }

    /**
     * 移除原始响应回调
     * 
     * @return this，支持链式调用
     */
    public AgentClient offRawResponse() {
        this.callbacks.setOnRawResponse(null);
        return this;
    }

    /**
     * 移除 Reasoning 回调
     * 
     * @return this，支持链式调用
     */
    public AgentClient offReasoning() {
        this.callbacks.setOnReasoning(null);
        return this;
    }

    /**
     * 断开连接（默认会话）
     */
    public void disconnect() {
        disconnect("");
    }

    /**
     * 断开指定会话的连接
     *
     * @param sessionId Session ID，如果为空字符串则断开默认连接
     */
    public void disconnect(String sessionId) {
        log.debug("Disconnecting WebSocket connection, sessionId: {}", FormatUtils.formatSessionId(sessionId));

        // 重置状态
        sessionManager.resetAllState(sessionId);

        // 移除会话上下文
        sessionManager.remove(sessionId);

        // 断开连接
        connectionManager.disconnect(sessionId);
    }

    /**
     * 关闭客户端并清理所有资源
     *
     * 遍历所有会话，逐个断开连接。用于完全退出客户端。
     */
    public void close() {
        try {
            // 清理所有会话的超时定时器
            for (String sessionId : sessionManager.getAllSessionIds()) {
                cleanupSessionTimers(sessionId);
            }

            // 断开所有连接
            for (String sessionId : sessionManager.getAllSessionIds()) {
                disconnect(sessionId);
            }
        } catch (Exception e) {
            log.error("Error during client close", e);
        } finally {
            // 确保 scheduler 总是被关闭
            shutdownScheduler();
        }
    }

    /**
     * 清理指定会话的所有定时器
     *
     * @param sessionId 会话 ID
     */
    private void cleanupSessionTimers(String sessionId) {
        try {
            StreamState state = sessionManager.getStreamState(sessionId);
            ScheduledFuture<?> timeoutId = state.getTimeoutId();
            if (timeoutId != null && !timeoutId.isDone()) {
                timeoutId.cancel(false);
            }
            state.setTimeoutId(null);
            state.setRefreshTimeout(null);
        } catch (Exception e) {
            log.debug("Error cleaning up session timers, sessionId: {}",
                    FormatUtils.formatSessionId(sessionId), e);
        }
    }

    /**
     * 关闭定时任务调度器
     * 使用 try-finally 确保总是被关闭，避免资源泄漏
     */
    private void shutdownScheduler() {
        if (scheduler.isShutdown()) {
            return;
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Scheduler did not terminate gracefully, forcing shutdown");
                scheduler.shutdownNow();
                // 再次等待
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

    /**
     * Spring Bean 销毁时的回调方法
     * 实现 DisposableBean 接口，确保在 Spring 容器关闭时自动清理资源
     */
    @Override
    public void destroy() {
        close();
    }

    /**
     * 获取客户端配置
     * 
     * @return 客户端配置对象
     */
    @NonNull
    public AgentConfig getConfig() {
        return config;
    }

    /**
     * 获取当前会话状态
     */
    public SessionState getSessionState(String sessionId) {
        return sessionManager.getSessionState(sessionId);
    }

    /**
     * 获取会话上下文
     * 
     * @param sessionId 会话 ID
     * @return 会话上下文对象
     */
    public SessionContext getSessionContext(String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    /**
     * 发送非流式请求
     * 
     * 如果之前调用过 session() 设置了默认会话ID，且当前链式调用未显式指定 sessionId，
     * 则自动使用默认会话ID。
     * 
     * @return 完整的响应文本（不为 null）
     */
    @NonNull
    public String send() {
        // 构建请求配置，使用默认会话ID（如果设置了的话）
        String effectiveDefaultSession = defaultSessionId != null ? defaultSessionId : Constants.DEFAULT_SESSION_ID;
        RequestBuilder.RequestConfig requestConfig = requestBuilder.buildRequestConfig(effectiveDefaultSession);

        // 重置链式调用状态（但保留 defaultSessionId，供下次使用）
        requestBuilder.reset();

        // 合并会话默认选项
        ExtendedSendOptions mergedOptions = mergeSessionOptions(requestConfig.sessionId(), requestConfig.options());

        // 调用内部发送方法（非流式模式），等待完成并返回结果
        return sendInternal(requestConfig.question(), false, null, null, requestConfig.sessionId(), mergedOptions)
                .join();
    }

    /**
     * 发送流式请求
     * 
     * 如果之前调用过 session() 设置了默认会话ID，且当前链式调用未显式指定 sessionId，
     * 则自动使用默认会话ID。
     * 
     * @return AgentStream 流式对象，可以通过 stream.done().join() 等待流完成（不为 null）
     */
    @NonNull
    public AgentStream stream() {
        // 构建请求配置，使用默认会话ID（如果设置了的话）
        String effectiveDefaultSession = defaultSessionId != null ? defaultSessionId : Constants.DEFAULT_SESSION_ID;
        RequestBuilder.RequestConfig requestConfig = requestBuilder.buildRequestConfig(effectiveDefaultSession);

        // 重置链式调用状态（但保留 defaultSessionId，供下次使用）
        requestBuilder.reset();

        // 合并会话默认选项
        ExtendedSendOptions mergedOptions = mergeSessionOptions(requestConfig.sessionId(), requestConfig.options());

        // 获取流式状态
        StreamState state = sessionManager.getStreamState(requestConfig.sessionId());

        // 创建流完成 Promise
        CompletableFuture<Void> completionFuture = new CompletableFuture<>();
        state.getPromiseHandlers().setStreamCompletionResolve(() -> completionFuture.complete(null));
        state.getPromiseHandlers().setStreamCompletionReject(completionFuture::completeExceptionally);

        // 调用内部流式发送方法，等待流对象创建完成
        AgentStream stream = streamInternal(
                requestConfig.question(),
                mergedOptions,
                requestConfig.sessionId(),
                requestConfig.onChunk()).join();

        // 设置完成 future
        stream.setCompletionFuture(completionFuture);

        // 返回 AgentStream 对象
        return stream;
    }

    /**
     * 内部流式发送实现
     */
    private CompletableFuture<AgentStream> streamInternal(
            @NonNull String question,
            @Nullable ExtendedSendOptions options,
            @NonNull String sessionId,
            @Nullable Consumer<String> onChunk) {

        ExtendedSendOptions mergedOptions = mergeSessionOptions(sessionId, options);

        // 如果提供了 onChunk 回调，使用回调模式
        if (onChunk != null) {
            return sendInternal(question, true, onChunk, null, sessionId, mergedOptions)
                    .thenApply(v -> new AgentStream(sessionId, streamManager, sessionManager));
        }

        // 迭代器模式：先发送请求
        return sendInternal(question, true, null, null, sessionId, mergedOptions)
                .thenApply(v -> new AgentStream(sessionId, streamManager, sessionManager));
    }

    /**
     * 内部发送实现
     */
    private CompletableFuture<String> sendInternal(
            @NonNull String question,
            boolean stream,
            @Nullable Consumer<String> onChunk,
            @Nullable Consumer<String> onComplete,
            @NonNull String sessionId,
            @Nullable ExtendedSendOptions options) {

        CompletableFuture<String> future = new CompletableFuture<>();

        // 确保连接已建立
        WebSocketConnection connection = getConnection(sessionId);

        if (!connection.isConnected()) {
            connectionManager.connect(sessionId).thenRun(() -> {
                executeSend(future, question, stream, onChunk, onComplete, sessionId, options);
            });
        } else {
            executeSend(future, question, stream, onChunk, onComplete, sessionId, options);
        }

        return future;
    }

    /**
     * 执行发送
     */
    private void executeSend(
            @NonNull CompletableFuture<String> future,
            @NonNull String question,
            boolean stream,
            @Nullable Consumer<String> onChunk,
            @Nullable Consumer<String> onComplete,
            @NonNull String sessionId,
            @Nullable ExtendedSendOptions options) {

        // 获取状态
        StreamState state = sessionManager.getStreamState(sessionId);
        try {

            // 检查是否正在发送（原子操作）
            // 使用 synchronized 确保检查和设置的原子性，防止并发竞态条件
            // 将状态设置操作也放在同步块内，确保状态一致性
            synchronized (state.getSendState()) {
                if (state.getSendState().isSending()) {
                    future.completeExceptionally(new IllegalStateException("上一个请求正在处理中，请等待完成"));
                    return;
                }
                // 立即设置 sending 状态，防止其他线程同时通过检查
                state.getSendState().setSending(true);

                // 在同步块内设置其他关键状态，确保原子性
                // 注意：这些状态设置应该在发送状态设置之后，但在同步块内完成
                state.setSessionState(SessionState.CHATTING);
                state.getStreamBuffer().getBuffer().setLength(0);
                state.getStreamBuffer().setMsgId("");
                state.getStreamBuffer().setDialogId(null);
                state.getStreamBuffer().setQuestion(question); // 保存当前请求的问题
                state.getStreamConfig().setCurrentNodeId(null);
                state.getStreamConfig().setCurrentStream(stream);
                state.getCallbacks().setCurrentOnChunk(onChunk);
                state.getCallbacks().setCurrentOnComplete(onComplete);
            }
            // 清理之前的超时定时器和刷新函数（如果存在）
            // 使用 try-finally 确保定时器总是被清理，避免资源泄漏
            ScheduledFuture<?> oldTimeoutId = state.getTimeoutId();
            if (oldTimeoutId != null) {
                try {
                    if (!oldTimeoutId.isDone()) {
                        oldTimeoutId.cancel(false);
                    }
                } catch (Exception e) {
                    log.debug("Error canceling old timeout timer", e);
                } finally {
                    state.setTimeoutId(null);
                }
            }
            state.setRefreshTimeout(null);

            // 如果是流式模式，准备流
            if (stream) {
                streamManager.prepareStream(sessionId);
                state.getStreamConfig().setActiveStreamId(sessionId);
            }

            // 构建载荷
            WSPayload payload = PayloadBuilder.buildPayload(config, question, sessionId, options);

            // 触发原始请求回调
            SessionContext.AgentCallbacks callbacks = getCallbacks(sessionId);
            if (callbacks.getOnRawRequest() != null) {
                callbacks.getOnRawRequest().accept(payload);
            }

            // 设置 Promise 处理器
            state.getPromiseHandlers().setPendingResolve(result -> {
                future.complete(Objects.requireNonNullElse(result, "sent"));
            });
            state.getPromiseHandlers().setPendingReject(future::completeExceptionally);

            // 获取超时时间（从配置中获取，默认 10000ms）
            long timeout = config.getWebsocket().getTimeout() != null
                    ? config.getWebsocket().getTimeout()
                    : Constants.DEFAULT_RESPONSE_TIMEOUT;

            // 保存超时时间到 state，用于刷新超时定时器
            state.setTimeout(timeout);

            // 设置响应超时定时器的辅助函数
            Runnable setupTimeout = () -> {
                // 清除旧的超时定时器（如果存在）
                ScheduledFuture<?> existingTimeoutId = state.getTimeoutId();
                if (existingTimeoutId != null) {
                    try {
                        if (!existingTimeoutId.isDone()) {
                            existingTimeoutId.cancel(false);
                        }
                    } catch (Exception e) {
                        log.debug("Error canceling old timeout timer in setupTimeout", e);
                    }
                }

                // 设置新的响应超时定时器
                ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
                    // 检查是否仍在发送状态（可能已经收到响应）
                    if (state.getSendState().isSending()) {
                        // 更新状态
                        state.getSendState().setSending(false);
                        state.setTimeoutId(null);

                        // 创建超时错误
                        AgentException timeoutError = AgentException.responseTimeout(timeout);

                        // 如果是流式模式，将超时错误推送到流队列
                        if (state.getStreamConfig().getActiveStreamId() != null) {
                            StreamManager.StreamQueueItem item = new StreamManager.StreamQueueItem();
                            item.setValue("");
                            item.setDone(true);
                            item.setError(timeoutError);
                            streamManager.enqueue(sessionId, item);
                        }

                        // 使用 ErrorHandler 统一处理错误
                        SessionContext.AgentCallbacks stateCallbacks = getCallbacks(sessionId);
                        // 触发错误回调
                        if (stateCallbacks.getOnError() != null) {
                            stateCallbacks.getOnError().accept(timeoutError);
                        }
                        log.warn("Response timeout, sessionId: {}, timeout: {}ms",
                                FormatUtils.formatSessionId(sessionId), timeout);

                        // Reject 流完成 Promise（让 stream.done() 能够抛出异常）
                        if (state.getPromiseHandlers().getStreamCompletionReject() != null) {
                            state.getPromiseHandlers().getStreamCompletionReject().accept(timeoutError);
                            state.getPromiseHandlers().setStreamCompletionResolve(null);
                            state.getPromiseHandlers().setStreamCompletionReject(null);
                        }

                        // 清空 Promise 处理器
                        state.getPromiseHandlers().setPendingResolve(null);
                        state.getPromiseHandlers().setPendingReject(null);
                    }
                }, timeout, TimeUnit.MILLISECONDS);

                // 保存超时定时器 ID
                state.setTimeoutId(timeoutFuture);
            };

            // 初始设置超时定时器
            setupTimeout.run();

            // 保存刷新超时定时器的函数到 state，供 MessageHandler 使用
            // 当收到新的 chunk 时，可以调用此函数刷新超时定时器
            state.setRefreshTimeout(setupTimeout);

            // 发送消息
            WebSocketConnection connection = getConnection(sessionId);
            connection.send(payload);

            // 流式模式：发送成功后立即 resolve
            if (stream) {
                future.complete("sent");
            }
        } catch (Exception e) {
            // 异常情况下，确保清理超时定时器，避免资源泄漏
            // 使用 try-finally 确保定时器总是被清理
            ScheduledFuture<?> timeoutId = state.getTimeoutId();
            if (timeoutId != null) {
                try {
                    if (!timeoutId.isDone()) {
                        timeoutId.cancel(false);
                    }
                } catch (Exception cleanupException) {
                    log.debug("Error canceling timeout timer during exception", cleanupException);
                } finally {
                    state.setTimeoutId(null);
                }
            }
            state.setRefreshTimeout(null);
            future.completeExceptionally(e);
        }
    }

    /**
     * 获取连接
     *
     * 确保消息处理器只注册一次，避免重复注册导致消息被重复处理
     *
     * @param sessionId 会话 ID（如果为 null，使用默认会话 ID）
     * @return WebSocket 连接实例（不为 null）
     */
    @NonNull
    private WebSocketConnection getConnection(@Nullable String sessionId) {
        // 防御性检查：使用 final 变量保存最终的 sessionId，以便在 lambda 中使用
        final String effectiveSessionId = (sessionId == null) ? Constants.DEFAULT_SESSION_ID : sessionId;

        WebSocketConnection connection = connectionManager.getConnection(effectiveSessionId);
        SessionContext context = sessionManager.getSession(effectiveSessionId);

        // 注册消息处理器（确保只注册一次）
        // 使用双重检查模式避免并发注册
        if (context.getMessageHandler() == null) {
            synchronized (context) {
                // 再次检查，避免并发注册
                if (context.getMessageHandler() == null) {
                    // 创建消息处理器（使用 final 变量 effectiveSessionId）
                    WebSocketConnection.WSMessageHandler handler = response -> handleWebSocketMessage(response,
                            effectiveSessionId);
                    // 注册消息处理器
                    connection.onMessage(handler);
                    context.setMessageHandler(handler);
                }
            }
        }

        context.setConnection(connection);
        return connection;
    }

    /**
     * 处理 WebSocket 消息
     *
     * @param response  WebSocket 响应消息
     * @param sessionId 会话 ID
     */
    private void handleWebSocketMessage(@Nullable WSResponse response, @Nullable String sessionId) {
        // 防御性检查
        if (response == null) {
            log.warn("Received null response, sessionId: {}", FormatUtils.formatSessionId(sessionId));
            return;
        }
        if (sessionId == null) {
            log.warn("SessionId is null when handling WebSocket message");
            return;
        }

        StreamState state = sessionManager.getStreamState(sessionId);
        SessionContext.AgentCallbacks callbacks = getCallbacks(sessionId);

        try {
            // 使用 MessageHandler 处理消息
            MessageHandler.MessageHandleResult result = messageHandler.handle(response, sessionId, state, callbacks);

            if (!result.isShouldContinue()) {
                return;
            }

            // 保存 dialog_id
            String dialogId = response.getDialogId();
            if (dialogId != null && state.getStreamBuffer().getDialogId() == null) {
                state.getStreamBuffer().setDialogId(dialogId);
            }

            // 处理流式响应片段
            // 注意：只有在流式模式下才处理流式片段，非流式模式下忽略流式片段
            boolean isStreamMode = state.getStreamConfig().isCurrentStream();
            if (isStreamMode && !result.isFinal() && result.isHasContent()
                    && !ResponseType.isType(response.getType(), ResponseType.FLOW)) {
                messageHandler.appendStreamContent(result.getText(), sessionId, state, callbacks);
                return;
            }

            // 处理消息完成
            if (result.isFinal()) {
                // 在状态重置前保存对话历史
                String question = state.getStreamBuffer().getQuestion();
                String answer = state.getStreamBuffer().getBuffer().toString();
                String dialogIdFromBuffer = state.getStreamBuffer().getDialogId();

                // 如果问题和答案都存在，保存到对话历史
                if (question != null && !question.isEmpty() && !answer.isEmpty()) {
                    SessionContext context = sessionManager.getSession(sessionId);
                    // 创建 user 消息
                    MessageParam userMessage = MessageParam.builder()
                            .role("user")
                            .content(question)
                            .dialogId(dialogIdFromBuffer)
                            .build();
                    // 创建 assistant 消息
                    MessageParam assistantMessage = MessageParam.builder()
                            .role("assistant")
                            .content(answer)
                            .dialogId(dialogIdFromBuffer)
                            .build();
                    // 添加到对话历史
                    context.addHistory(userMessage);
                    context.addHistory(assistantMessage);
                }

                messageHandler.handleMessageComplete(result.getText(), result.isHasContent(), sessionId, state,
                        callbacks);
            }
        } catch (Exception e) {
            // 统一错误处理：清理状态并处理错误
            // 注意：state 在 try 块之前已初始化，getStreamState 总是返回非 null

            // 步骤1: 清理超时定时器
            try {
                if (state.getTimeoutId() != null) {
                    state.getTimeoutId().cancel(false);
                    state.setTimeoutId(null);
                }
                state.setRefreshTimeout(null);
            } catch (Exception cleanupException) {
                log.debug("Error cleaning up timeout timer", cleanupException);
            }

            // 步骤2: 更新发送状态
            try {
                state.getSendState().setSending(false);
            } catch (Exception stateException) {
                log.debug("Error updating send state", stateException);
            }

            // 步骤3: 包装错误，确保保留原始错误信息
            AgentException wrappedError = AgentException.wrap(e);

            // 步骤4: 如果是流式模式，将错误推送到流队列
            try {
                if (streamManager.isActive(sessionId)) {
                    StreamManager.StreamQueueItem item = new StreamManager.StreamQueueItem();
                    item.setValue("");
                    item.setDone(true);
                    item.setError(wrappedError);
                    streamManager.enqueue(sessionId, item);
                }
            } catch (Exception streamException) {
                log.debug("Error enqueueing error to stream", streamException);
            }

            // 步骤5: 如果有 reject 函数，调用它
            try {
                if (state.getPromiseHandlers().getPendingReject() != null) {
                    state.getPromiseHandlers().getPendingReject().accept(wrappedError);
                    // 清空 Promise 处理器
                    state.getPromiseHandlers().setPendingReject(null);
                    state.getPromiseHandlers().setPendingResolve(null);
                }
                // 同时处理流完成 Promise
                if (state.getPromiseHandlers().getStreamCompletionReject() != null) {
                    state.getPromiseHandlers().getStreamCompletionReject().accept(wrappedError);
                    state.getPromiseHandlers().setStreamCompletionResolve(null);
                    state.getPromiseHandlers().setStreamCompletionReject(null);
                }
            } catch (Exception promiseException) {
                log.debug("Error handling promise rejection", promiseException);
            }

            // 步骤6: 使用 ErrorHandler 统一处理错误
            // 确保错误信息完整，包含上下文信息
            log.error("Failed to process WebSocket message, sessionId: {}",
                    FormatUtils.formatSessionId(sessionId), e);
            try {
                // 使用统一的异常处理方法
                errorHandler.handleException(wrappedError, sessionId, callbacks);
            } catch (Exception handlerException) {
                log.error("Error in error handler", handlerException);
            }
        }
    }

    /**
     * 获取回调（Client 级别回调，所有 session 共享）
     *
     * @param sessionId 会话 ID（未使用，保留以保持接口一致性）
     * @return 回调对象
     */
    private SessionContext.@NonNull AgentCallbacks getCallbacks(@Nullable String sessionId) {
        return this.callbacks;
    }

    /**
     * 合并会话默认选项
     *
     * @param sessionId 会话 ID
     * @param options   当前选项
     * @return 合并后的选项
     */
    private ExtendedSendOptions mergeSessionOptions(@Nullable String sessionId, @Nullable ExtendedSendOptions options) {
        // 防御性检查：如果 options 为 null，返回默认选项
        if (options == null) {
            options = new ExtendedSendOptions();
        }

        // 防御性检查：如果 sessionId 为 null，直接返回当前选项
        if (sessionId == null) {
            return options;
        }

        SessionContext context = sessionManager.getSession(sessionId);
        ExtendedSendOptions sessionOptions = context.getConfig();
        if (sessionOptions == null) {
            return options;
        }

        // 合并：当前选项覆盖会话默认选项
        // 先合并会话默认选项（只合并非null字段）
        ExtendedSendOptions merged = mergeOptionsNonNull(new ExtendedSendOptions(), sessionOptions);
        // 再合并当前选项（覆盖会话默认选项）
        return mergeOptionsNonNull(merged, options);
    }

    /**
     * 合并选项，只合并非null字段
     *
     * @param base     基础选项
     * @param override 覆盖选项
     * @return 合并后的选项
     */
    private ExtendedSendOptions mergeOptionsNonNull(@Nullable ExtendedSendOptions base,
            @Nullable ExtendedSendOptions override) {
        // 防御性检查：如果 base 为 null，创建新实例
        if (base == null) {
            base = new ExtendedSendOptions();
        }
        if (override == null) {
            return base;
        }
        ExtendedSendOptions merged = ClientUtils.mergeOptions(base, new ExtendedSendOptions());
        if (override.getExtraHeader() != null) {
            merged.setExtraHeader(override.getExtraHeader());
        }
        if (override.getExtraBody() != null) {
            merged.setExtraBody(override.getExtraBody());
        }
        if (override.getMessageParams() != null) {
            merged.setMessageParams(override.getMessageParams());
        }
        if (override.getChatHistory() != null) {
            merged.setChatHistory(override.getChatHistory());
        }
        if (override.getTipMessageExtra() != null) {
            merged.setTipMessageExtra(override.getTipMessageExtra());
        }
        if (override.getTipMessageParams() != null) {
            merged.setTipMessageParams(override.getTipMessageParams());
        }
        if (override.getModelOptions() != null) {
            merged.setModelOptions(override.getModelOptions());
        }
        return merged;
    }

}
