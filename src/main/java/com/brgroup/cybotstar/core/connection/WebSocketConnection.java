package com.brgroup.cybotstar.core.connection;

import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.agent.exception.AgentException;
import com.brgroup.cybotstar.core.model.common.ConnectionState;
import com.brgroup.cybotstar.core.model.common.ResponseType;
import com.brgroup.cybotstar.core.model.ws.WSPayload;
import com.brgroup.cybotstar.core.model.ws.WSResponse;
import com.brgroup.cybotstar.core.util.CybotStarConstants;
import com.brgroup.cybotstar.core.util.payload.PayloadBuilder;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 响应式 WebSocket 连接
 * 完全基于 Project Reactor 的 WebSocket 连接实现
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class WebSocketConnection implements AutoCloseable {

    @NonNull
    private final AgentConfig config;

    // WebSocket 客户端
    private final AtomicReference<WebSocketClient> wsRef = new AtomicReference<>();

    // 消息流 Sink（使用 multicast 支持多个订阅者）
    private final Sinks.Many<WSResponse> messageSink = Sinks.many()
            .multicast()
            .directBestEffort();  // 使用 directBestEffort 支持多个订阅者

    // 连接状态流 Sink
    private final Sinks.Many<ConnectionState> stateSink = Sinks.many()
            .multicast()
            .directBestEffort();

    // 当前连接状态
    private final AtomicReference<ConnectionState> currentState =
            new AtomicReference<>(ConnectionState.DISCONNECTED);

    // 是否已关闭
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // 重连尝试次数
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    // 心跳流（用于取消订阅）
    private final AtomicReference<reactor.core.Disposable> heartbeatDisposable =
            new AtomicReference<>();

    public WebSocketConnection(@NonNull AgentConfig config) {
        this.config = config;
    }

    /**
     * 获取消息流
     * 返回所有接收到的 WebSocket 消息（不包括心跳）
     */
    @NonNull
    public Flux<WSResponse> messages() {
        return messageSink.asFlux();
    }

    /**
     * 获取连接状态流
     */
    @NonNull
    public Flux<ConnectionState> connectionStates() {
        return stateSink.asFlux()
                .startWith(currentState.get());
    }

    /**
     * 获取当前连接状态
     */
    @NonNull
    public ConnectionState getCurrentState() {
        return currentState.get();
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        WebSocketClient ws = wsRef.get();
        return currentState.get() == ConnectionState.CONNECTED
                && ws != null
                && ws.isOpen();
    }

    /**
     * 连接到 WebSocket 服务器
     * 返回 Mono，连接成功时 complete
     */
    @NonNull
    public Mono<Void> connect() {
        // 如果已连接，直接返回
        if (isConnected()) {
            return Mono.empty();
        }

        // 如果已关闭，返回错误
        if (closed.get()) {
            return Mono.error(new IllegalStateException("Connection is closed"));
        }

        return Mono.<Void>create(sink -> {
            try {
                setState(ConnectionState.CONNECTING);

                String url = config.getWebsocket().getUrl();
                if (url == null || url.isEmpty()) {
                    sink.error(new IllegalArgumentException("WebSocket URL 未配置"));
                    return;
                }

                URI serverUri = URI.create(url);
                WebSocketClient ws = new WebSocketClient(serverUri) {
                    @Override
                    public void onOpen(ServerHandshake handshake) {
                        log.debug("WebSocket connection opened");
                        setState(ConnectionState.CONNECTED);
                        startHeartbeat();
                        sink.success();
                    }

                    @Override
                    public void onMessage(String message) {
                        if (message != null && !message.isEmpty()) {
                            handleMessage(message);
                        }
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        log.debug("WebSocket connection closed, code: {}, reason: {}", code, reason);
                        handleClose();
                    }

                    @Override
                    public void onError(Exception ex) {
                        log.error("WebSocket connection error", ex);
                        handleError(ex);
                    }
                };

                wsRef.set(ws);
                ws.connect();

            } catch (Exception e) {
                setState(ConnectionState.DISCONNECTED);
                sink.error(AgentException.connectionFailed("创建 WebSocket 失败", e));
            }
        })
        .timeout(Duration.ofMillis(config.getWebsocket().getTimeout() != null
                ? config.getWebsocket().getTimeout()
                : CybotStarConstants.DEFAULT_WS_TIMEOUT))
        .doOnError(error -> {
            setState(ConnectionState.DISCONNECTED);
            log.error("Failed to connect to WebSocket", error);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 确保连接已建立
     * 如果未连接，则自动连接
     */
    @NonNull
    public Mono<Void> ensureConnected() {
        if (isConnected()) {
            return Mono.empty();
        }
        return connect();
    }

    /**
     * 发送消息
     */
    @NonNull
    public Mono<Void> send(@NonNull WSPayload payload) {
        return Mono.defer(() -> {
            WebSocketClient ws = wsRef.get();
            if (ws == null || !ws.isOpen()) {
                return Mono.error(AgentException.sendFailed("WebSocket 未连接"));
            }

            try {
                String data = JSON.toJSONString(payload);
                log.debug("⬆️ [WS] Sending message, question_preview={}",
                        payload.getQuestion() != null
                                ? payload.getQuestion().substring(0, Math.min(20, payload.getQuestion().length())) + "..."
                                : "null");
                ws.send(data);
                return Mono.empty();
            } catch (Exception e) {
                return Mono.error(AgentException.sendFailed("发送消息失败", e));
            }
        });
    }

    /**
     * 关闭连接
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            stopHeartbeat();

            WebSocketClient ws = wsRef.get();
            if (ws != null) {
                try {
                    ws.close();
                } catch (Exception e) {
                    log.debug("Error closing WebSocket", e);
                }
            }

            setState(ConnectionState.CLOSED);
            messageSink.tryEmitComplete();
            stateSink.tryEmitComplete();
        }
    }

    /**
     * 处理接收到的消息
     */
    private void handleMessage(@NonNull String message) {
        try {
            WSResponse response = JSON.parseObject(message, WSResponse.class);

            // 忽略心跳响应
            if (ResponseType.isType(response.getType(), ResponseType.HEARTBEAT)) {
                log.debug("Ignoring heartbeat response");
                return;
            }

            // 推送到消息流
            Sinks.EmitResult result = messageSink.tryEmitNext(response);
            // 忽略 FAIL_ZERO_SUBSCRIBER（没有订阅者时）和 FAIL_TERMINATED（已关闭时）
            if (result.isFailure()
                    && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER
                    && result != Sinks.EmitResult.FAIL_TERMINATED) {
                log.warn("Failed to emit message, result: {}", result);
            }

        } catch (Exception e) {
            log.error("Failed to parse WebSocket message", e);
        }
    }

    /**
     * 处理连接关闭
     */
    private void handleClose() {
        stopHeartbeat();
        setState(ConnectionState.DISCONNECTED);

        // 如果配置了自动重连，则尝试重连
        Boolean autoReconnect = config.getWebsocket().getAutoReconnect();
        if (autoReconnect != null && autoReconnect && !closed.get()) {
            scheduleReconnect();
        }
    }

    /**
     * 处理连接错误
     */
    private void handleError(@NonNull Exception error) {
        Sinks.EmitResult result = messageSink.tryEmitError(error);
        // 忽略 FAIL_TERMINATED（已关闭时）
        if (result.isFailure() && result != Sinks.EmitResult.FAIL_TERMINATED) {
            log.warn("Failed to emit error, result: {}", result);
        }
    }

    /**
     * 设置连接状态
     */
    private void setState(@NonNull ConnectionState state) {
        ConnectionState oldState = currentState.getAndSet(state);
        if (oldState != state) {
            log.debug("WebSocket state changed: {} -> {}", oldState, state);
            Sinks.EmitResult result = stateSink.tryEmitNext(state);
            // 忽略 FAIL_ZERO_SUBSCRIBER（没有订阅者时）和 FAIL_TERMINATED（已关闭时）
            if (result.isFailure()
                    && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER
                    && result != Sinks.EmitResult.FAIL_TERMINATED) {
                log.warn("Failed to emit state, result: {}", result);
            }
        }
    }

    /**
     * 启动心跳
     */
    private void startHeartbeat() {
        Long interval = config.getWebsocket().getHeartbeatInterval();
        if (interval == null || interval <= 0) {
            return;
        }

        stopHeartbeat();

        reactor.core.Disposable disposable = Flux.interval(
                Duration.ofMillis(interval),
                Duration.ofMillis(interval),
                Schedulers.boundedElastic()
        )
        .flatMap(tick -> sendHeartbeat())
        .subscribe(
                v -> {},
                error -> log.debug("Heartbeat error", error)
        );

        heartbeatDisposable.set(disposable);
    }

    /**
     * 停止心跳
     */
    private void stopHeartbeat() {
        reactor.core.Disposable disposable = heartbeatDisposable.getAndSet(null);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    /**
     * 发送心跳
     */
    @NonNull
    private Mono<Void> sendHeartbeat() {
        return Mono.defer(() -> {
            if (!isConnected()) {
                return Mono.empty();
            }

            try {
                String heartbeat = PayloadBuilder.buildHeartbeatPayload();
                WebSocketClient ws = wsRef.get();
                if (ws != null && ws.isOpen()) {
                    ws.send(heartbeat);
                }
                return Mono.empty();
            } catch (Exception e) {
                log.debug("Failed to send heartbeat", e);
                return Mono.empty();
            }
        });
    }

    /**
     * 安排重连（使用指数退避策略）
     */
    private void scheduleReconnect() {
        int attempts = reconnectAttempts.incrementAndGet();

        Long baseRetryInterval = config.getWebsocket().getRetryInterval();
        if (baseRetryInterval == null) {
            baseRetryInterval = CybotStarConstants.DEFAULT_RETRY_INTERVAL;
        }

        // 指数退避：delay = min(baseInterval * 2^attempts, maxInterval)
        long maxRetryInterval = CybotStarConstants.MAX_RETRY_BACKOFF;
        long delay = Math.min(
                baseRetryInterval * (long) Math.pow(2, Math.min(attempts - 1, 5)),
                maxRetryInterval
        );

        log.debug("Scheduling reconnect attempt #{} after {}ms", attempts, delay);
        setState(ConnectionState.RECONNECTING);

        Mono.delay(Duration.ofMillis(delay))
                .then(connect())
                .subscribe(
                        v -> {
                            log.info("Reconnection successful after {} attempts", attempts);
                            reconnectAttempts.set(0);  // 重置重连计数
                        },
                        error -> {
                            log.warn("Reconnection attempt #{} failed", attempts, error);
                            if (!closed.get()) {
                                scheduleReconnect();
                            }
                        }
                );
    }
}
