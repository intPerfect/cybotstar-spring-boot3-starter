package com.brgroup.cybotstar.agent;

import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.agent.model.request.MessageParam;
import com.brgroup.cybotstar.core.connection.ConnectionManager;
import com.brgroup.cybotstar.agent.exception.AgentException;
import com.brgroup.cybotstar.agent.session.SessionContext;
import com.brgroup.cybotstar.agent.session.SessionContextManager;
import com.brgroup.cybotstar.agent.util.RequestBuilder;
import com.brgroup.cybotstar.agent.model.request.ExtendedSendOptions;
import com.brgroup.cybotstar.agent.model.ModelOptions;
import com.brgroup.cybotstar.agent.handler.ReactiveMessageHandler;
import com.brgroup.cybotstar.core.model.ws.WSPayload;
import com.brgroup.cybotstar.core.util.CybotStarConstants;
import com.brgroup.cybotstar.core.util.CybotStarUtils;
import com.brgroup.cybotstar.core.util.payload.PayloadBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 响应式 Agent 客户端
 * 完全基于 Project Reactor 的响应式实现
 * 移除所有 CompletableFuture、回调、队列
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class AgentClient implements DisposableBean {

    @NonNull
    private final AgentConfig config;

    @NonNull
    private final ConnectionManager connectionManager;

    @NonNull
    private final SessionContextManager sessionManager;

    // 使用 ThreadLocal 支持并发调用
    private final ThreadLocal<RequestBuilder> requestBuilderHolder = ThreadLocal.withInitial(RequestBuilder::new);
    private final ThreadLocal<String> threadLocalSessionId = new ThreadLocal<>();

    // 全局默认 session ID
    @Nullable
    private volatile String defaultSessionId;

    // 事件回调（保留用于兼容性）
    @Nullable
    private volatile Consumer<String> reasoningCallback;
    @Nullable
    private volatile Consumer<WSPayload> rawRequestCallback;
    @Nullable
    private volatile Consumer<Object> rawResponseCallback;

    public AgentClient(@NonNull AgentConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        CybotStarUtils.validateConfig(config);
        this.config = config;
        this.connectionManager = new ConnectionManager(config);
        this.sessionManager = new SessionContextManager(connectionManager);
        log.debug("ReactiveAgentClient initialized, URL: {}", config.getWebsocket().getUrl());
    }

    // ============================================================================
    // 链式方法
    // ============================================================================

    @NonNull
    public AgentClient prompt(@NonNull String question) {
        Objects.requireNonNull(question, "question cannot be null");
        requestBuilderHolder.get().prompt(question);
        return this;
    }

    @NonNull
    public AgentClient option(@NonNull ModelOptions modelOptions) {
        Objects.requireNonNull(modelOptions, "modelOptions cannot be null");
        requestBuilderHolder.get().option(modelOptions);
        return this;
    }

    @NonNull
    public AgentClient session(@NonNull String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        this.threadLocalSessionId.set(sessionId);
        this.defaultSessionId = sessionId;
        requestBuilderHolder.get().session(sessionId);
        return this;
    }

    @NonNull
    public AgentClient messages(@NonNull List<MessageParam> messages) {
        Objects.requireNonNull(messages, "messages cannot be null");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }
        requestBuilderHolder.get().setMessageParams(messages);
        return this;
    }

    /**
     * 设置请求超时时间
     */
    @NonNull
    public AgentClient timeout(@NonNull Duration timeout) {
        Objects.requireNonNull(timeout, "timeout cannot be null");
        requestBuilderHolder.get().timeout(timeout);
        return this;
    }

    /**
     * 设置请求超时时间（毫秒）
     */
    @NonNull
    public AgentClient timeout(long timeoutMillis) {
        requestBuilderHolder.get().timeout(timeoutMillis);
        return this;
    }

    // ============================================================================
    // 事件回调（保留用于兼容性）
    // ============================================================================

    @NonNull
    public AgentClient onReasoning(@NonNull Consumer<String> callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
        this.reasoningCallback = callback;
        return this;
    }

    @NonNull
    public AgentClient onRawRequest(@NonNull Consumer<WSPayload> callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
        this.rawRequestCallback = callback;
        return this;
    }

    @NonNull
    public AgentClient onRawResponse(@NonNull Consumer<Object> callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
        this.rawResponseCallback = callback;
        return this;
    }

    // ============================================================================
    // 响应式终端操作
    // ============================================================================

    /**
     * 流式发送，返回 Flux<String>（每个元素是一个 chunk）
     * 完全响应式实现，实时推送数据
     */
    @NonNull
    public Flux<String> stream() {
        // 构建请求配置
        RequestBuilder requestBuilder = requestBuilderHolder.get();
        String effectiveSessionId = getEffectiveSessionId();
        RequestBuilder.RequestConfig requestConfig = requestBuilder.buildRequestConfig(effectiveSessionId);
        requestBuilder.reset();

        // 清理 ThreadLocal
        requestBuilderHolder.remove();
        threadLocalSessionId.remove();

        final String sessionId = requestConfig.sessionId();
        final String question = requestConfig.question();
        ExtendedSendOptions options = requestConfig.options();
        Duration requestTimeout = requestConfig.timeout();

        // 获取超时时间（优先使用请求级超时，否则使用配置的默认超时）
        long timeout = requestTimeout != null
                ? requestTimeout.toMillis()
                : (config.getWebsocket().getTimeout() != null
                        ? config.getWebsocket().getTimeout()
                        : CybotStarConstants.DEFAULT_RESPONSE_TIMEOUT);

        // 保存回调引用
        final Consumer<String> reasoningCb = this.reasoningCallback;
        final Consumer<WSPayload> rawRequestCb = this.rawRequestCallback;
        final Consumer<Object> rawResponseCb = this.rawResponseCallback;

        // 响应式合并选项并发送请求
        return mergeOptionsReactive(sessionId, options)
                .flatMapMany(mergedOptions -> sessionManager.getContext(sessionId)
                        // 确保连接已建立
                        .flatMap(context -> context.getConnection().ensureConnected()
                                .thenReturn(context))
                        // 发送请求
                        .flatMap(context -> {
                            WSPayload payload = PayloadBuilder.buildPayload(config, question, sessionId, mergedOptions);

                            // 触发原始请求回调
                            if (rawRequestCb != null) {
                                rawRequestCb.accept(payload);
                            }

                            return context.getConnection().send(payload)
                                    .thenReturn(context);
                        })
                        // 获取事件流并处理
                        .flatMapMany(context -> {
                    // 累积完整文本用于保存历史
                    final StringBuilder fullTextBuilder = new StringBuilder();
                    final String finalQuestion = question;

                    // 如果需要原始响应回调，订阅原始消息流
                    if (rawResponseCb != null) {
                        context.messageStream()
                                .doOnNext(rawResponseCb::accept)
                                .subscribe(
                                    v -> {}, // onNext handled by doOnNext
                                    error -> log.error("Raw response callback error for session: {}", sessionId, error)
                                );
                    }

                    // 使用事件流
                    return context.eventStream()
                            // 处理 Reasoning 事件
                            .doOnNext(event -> {
                                if (event.getType() == ReactiveMessageHandler.MessageEventType.REASONING
                                        && reasoningCb != null) {
                                    reasoningCb.accept(event.getContent());
                                }
                            })
                            // 处理 COMPLETE 事件（触发流完成）
                            .takeWhile(event -> event.getType() != ReactiveMessageHandler.MessageEventType.COMPLETE)
                            // 只保留 CHUNK 事件
                            .filter(event -> event.getType() == ReactiveMessageHandler.MessageEventType.CHUNK)
                            .map(ReactiveMessageHandler.MessageEvent::getContent)
                            // 累积文本
                            .doOnNext(fullTextBuilder::append)
                            // 超时处理
                            .timeout(Duration.ofMillis(timeout), Flux.empty())
                            // 完成时保存历史
                            .doOnComplete(() -> {
                                String fullText = fullTextBuilder.toString();
                                if (!finalQuestion.isEmpty() && !fullText.isEmpty()) {
                                    context.addHistory(MessageParam.builder()
                                            .role("user")
                                            .content(finalQuestion)
                                            .build()).subscribe(
                                                v -> {},
                                                error -> log.error("Failed to save user history for session: {}", sessionId, error)
                                            );
                                    context.addHistory(MessageParam.builder()
                                            .role("assistant")
                                            .content(fullText)
                                            .build()).subscribe(
                                                v -> {},
                                                error -> log.error("Failed to save assistant history for session: {}", sessionId, error)
                                            );
                                }
                            });
                        })
                )
                // 错误处理
                .onErrorResume(error -> {
                    log.error("Stream error, sessionId: {}", sessionId, error);
                    return Flux.error(AgentException.wrap(error));
                });
    }

    /**
     * 非流式发送，返回 Mono<String>（完整响应文本）
     */
    @NonNull
    public Mono<String> send() {
        return stream()
                .reduce(new StringBuilder(), StringBuilder::append)
                .map(StringBuilder::toString);
    }

    // ============================================================================
    // 辅助方法
    // ============================================================================

    /**
     * 获取当前有效的 session ID
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

    /**
     * 合并选项（响应式版本）
     */
    @NonNull
    private Mono<ExtendedSendOptions> mergeOptionsReactive(@NonNull String sessionId, @Nullable ExtendedSendOptions options) {
        final ExtendedSendOptions finalOptions = options != null ? options : new ExtendedSendOptions();

        // 获取会话上下文的历史消息
        return sessionManager.getContext(sessionId)
                .map(context -> {
                    List<MessageParam> historyMessages = context.getHistory();
                    if (historyMessages != null && !historyMessages.isEmpty()) {
                        List<MessageParam> existingParams = finalOptions.getMessageParams();
                        List<MessageParam> finalParams = new ArrayList<>();

                        // 先添加历史消息
                        finalParams.addAll(historyMessages);

                        // 再添加现有的 messageParams
                        if (existingParams != null && !existingParams.isEmpty()) {
                            finalParams.addAll(existingParams);
                        }

                        finalOptions.setMessageParams(finalParams);
                    }
                    return finalOptions;
                });
    }

    /**
     * 获取会话上下文（响应式方法，推荐使用）
     */
    @NonNull
    public Mono<SessionContext> getSessionContextReactive(@NonNull String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        return sessionManager.getContext(sessionId);
    }

    /**
     * 获取会话上下文（同步方法，用于兼容旧 API）
     * @deprecated 推荐使用 {@link #getSessionContextReactive(String)} 以避免阻塞
     */
    @Deprecated
    @NonNull
    public SessionContext getSessionContext(@NonNull String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        return sessionManager.getContext(sessionId).block();
    }

    /**
     * 断开连接
     */
    public void disconnect(@NonNull String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        sessionManager.removeContext(sessionId).subscribe();
    }

    /**
     * 关闭客户端
     */
    public void close() {
        sessionManager.removeAll()
                .then(connectionManager.disconnectAll())
                .subscribe(
                        v -> log.debug("AgentClient closed"),
                        error -> log.error("Error closing AgentClient", error)
                );
    }

    @Override
    public void destroy() {
        close();
    }
}
