package com.brgroup.cybotstar.connection;

import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.agent.exception.AgentException;
import com.brgroup.cybotstar.model.common.ConnectionState;
import com.brgroup.cybotstar.model.common.ResponseType;
import com.brgroup.cybotstar.model.ws.WSPayload;
import com.brgroup.cybotstar.model.ws.WSResponse;
import com.brgroup.cybotstar.util.Constants;
import com.brgroup.cybotstar.util.payload.PayloadBuilder;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket 连接封装
 * 提供 WebSocket 连接管理功能，支持：
 * - 自动重连
 * - 心跳保活
 * - 连接状态管理
 * - 消息处理器注册
 *
 * 实现 AutoCloseable 接口，支持 try-with-resources 语法
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class WebSocketConnection implements AutoCloseable {

    // ========================================================================
    // 核心连接相关
    // ========================================================================
    /** WebSocket 客户端实例 */
    private WebSocketClient ws;

    /** 客户端配置 */
    @NonNull
    private final AgentConfig config;

    /** 连接状态（使用 volatile 确保多线程环境下的可见性） */
    private volatile ConnectionState wsState = ConnectionState.DISCONNECTED;

    /** 连接状态原子引用（用于状态 CAS，保证 connect 幂等性） */
    private final AtomicReference<ConnectionState> connectStateRef = new AtomicReference<>(
            ConnectionState.DISCONNECTED);

    /** 连接 Future 引用（使用 AtomicReference 实现无锁 CAS 操作，提升并发性能） */
    private final AtomicReference<CompletableFuture<Void>> connectFutureRef = new AtomicReference<>();

    // ========================================================================
    // 定时器相关
    // ========================================================================
    /** 定时任务调度器 */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /** 重连定时器 */
    private ScheduledFuture<?> reconnectTimer;

    /** 重连尝试次数（使用 AtomicInteger 确保线程安全） */
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    /** 心跳定时器 */
    private ScheduledFuture<?> heartbeatTimer;

    /** 连接超时定时器 */
    private ScheduledFuture<?> connectTimeoutTimer;

    // ========================================================================
    // 处理器相关
    // ========================================================================
    /** 消息处理器集合 */
    private final Set<WSMessageHandler> messageHandlers = ConcurrentHashMap.newKeySet();

    /** 状态变化处理器集合 */
    private final Set<WSStateHandler> stateHandlers = ConcurrentHashMap.newKeySet();

    /** 回调执行器（用于异步执行状态回调，避免阻塞） */
    private final Executor callbackExecutor = ForkJoinPool.commonPool();

    // ========================================================================
    // 状态信息相关
    // ========================================================================
    /** 连接关闭信息 */
    private CloseInfo closeInfo;

    /** 连接错误信息（用于在 onClose 中完成 Future） */
    private Exception connectionError;

    /**
     * WebSocket 消息处理器接口
     */
    @FunctionalInterface
    public interface WSMessageHandler {
        void handle(@NonNull WSResponse response);
    }

    /**
     * WebSocket 连接状态变化处理器接口
     */
    @FunctionalInterface
    public interface WSStateHandler {
        void handle(@NonNull ConnectionState state);
    }

    /**
     * 连接关闭信息
     */
    private record CloseInfo(int code, String reason) {
    }

    public WebSocketConnection(@NonNull AgentConfig config) {
        this.config = config;
    }

    /**
     * 获取当前连接状态
     */
    @NonNull
    public ConnectionState getState() {
        return wsState;
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return wsState == ConnectionState.CONNECTED && ws != null && ws.isOpen();
    }

    /**
     * 建立 WebSocket 连接
     * 
     * 使用状态 CAS 和 Future 引用 CAS 双重保护，保证 connect 方法的幂等性。
     * 只有从 DISCONNECTED 或 NOT_EXIST 状态 CAS 到 CONNECTING 的线程才能执行连接。
     *
     * @return CompletableFuture，连接成功时 complete
     */
    @NonNull
    public CompletableFuture<Void> connect() {
        // 如果已连接，直接返回已完成的 future
        if (isConnected()) {
            log.debug("WebSocket already connected, returning completed future");
            return CompletableFuture.completedFuture(null);
        }

        // 使用状态 CAS 保证幂等性：检查是否正在连接中
        ConnectionState currentState = connectStateRef.get();
        if (currentState == ConnectionState.CONNECTING) {
            // 正在连接中，返回正在进行的 future
            CompletableFuture<Void> existing = connectFutureRef.get();
            if (existing != null) {
                log.debug("WebSocket connecting, returning in-progress future");
                return existing;
            }
        }

        // 尝试从 DISCONNECTED 或 NOT_EXIST 转换到 CONNECTING
        if (!connectStateRef.compareAndSet(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING) &&
                !connectStateRef.compareAndSet(ConnectionState.NOT_EXIST, ConnectionState.CONNECTING)) {
            // CAS 失败，说明其他线程已经开始连接
            CompletableFuture<Void> existing = connectFutureRef.get();
            if (existing != null) {
                log.debug("WebSocket connecting by another thread, returning existing future");
                return existing;
            }
            // 如果 existing 为 null，可能是状态不一致，重新检查
            currentState = connectStateRef.get();
            if (currentState == ConnectionState.CONNECTING) {
                existing = connectFutureRef.get();
                if (existing != null) {
                    return existing;
                }
            }
        }

        // 创建新的连接 future
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!connectFutureRef.compareAndSet(null, future)) {
            // CAS 失败，说明其他线程已经创建了 future
            CompletableFuture<Void> currentFuture = connectFutureRef.get();
            if (currentFuture != null) {
                log.debug("WebSocket connecting by another thread, returning existing future");
                return currentFuture;
            }
            // 如果 currentFuture 为 null，重置状态并返回新创建的 future
            connectStateRef.set(ConnectionState.DISCONNECTED);
            return future;
        }

        // 只有 CAS 成功的线程才能走到这里
        // 执行实际连接逻辑
        doConnect(future);
        return future;
    }

    /**
     * 执行实际的连接逻辑
     * 
     * @param future 连接 Future，用于通知连接结果
     */
    private void doConnect(@NonNull CompletableFuture<Void> future) {
        long timeout = config.getWebsocket().getTimeout() != null
                ? config.getWebsocket().getTimeout()
                : Constants.DEFAULT_WS_TIMEOUT;
        setState(ConnectionState.CONNECTING);

        try {
            String url = config.getWebsocket().getUrl();
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("WebSocket URL 未配置");
            }
            URI serverUri = URI.create(url);
            ws = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.debug("WebSocket connection opened");
                    // 取消连接超时定时器（如果存在）
                    cancelConnectTimeoutTimer();
                    setState(ConnectionState.CONNECTED);
                    reconnectAttempts.set(0);
                    startHeartbeat();
                    // 清理 connectFuture 引用
                    connectFutureRef.set(null);
                    future.complete(null);
                }

                @Override
                public void onMessage(String message) {
                    // 只打印一次关键信息，去重
                    if (message != null && !message.isEmpty()) {
                        // 提取关键字段打印一行
                        try {
                            WSResponse resp = JSON.parseObject(message, WSResponse.class);
                            Object data = resp.getData();
                            String dataPreview = "null";
                            if (data != null) {
                                String dataStr = data.toString();
                                if (!dataStr.isEmpty()) {
                                    dataPreview = dataStr.substring(0, Math.min(30, dataStr.length()));
                                }
                            }
                            log.debug("⬇️ [WS] code={}, index={}, type={}, finish={}, dialog_id={}, data_preview={}",
                                    resp.getCode(), resp.getIndex(), resp.getType(), resp.getFinish(),
                                    resp.getDialogId(), dataPreview);
                        } catch (RuntimeException e) {
                            // JSON 解析失败或其他运行时异常，记录调试信息
                            log.debug("⬇️ [WS] Received message length: {}", message.length());
                        }
                        handleMessage(message);
                    } else {
                        log.warn("Received empty message!");
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.debug("WebSocket connection closed, code: {}, reason: {}, remote: {}", code, reason, remote);
                    closeInfo = new CloseInfo(code, reason);
                    handleClose(code, reason, remote);
                }

                @Override
                public void onError(Exception ex) {
                    log.error("WebSocket connection error", ex);
                    // 只记录错误，保存错误信息，不完成 Future，不设置状态
                    // 让 onClose 统一处理连接结束逻辑
                    CompletableFuture<Void> currentFuture = connectFutureRef.get();
                    if (currentFuture == future) {
                        // 保存错误信息，供 handleClose 使用
                        connectionError = ex;
                        // 关闭连接，触发 onClose
                        if (ws != null) {
                            ws.close();
                        }
                    }
                }
            };

            // 连接超时处理
            // 保存定时器引用，以便在连接成功时立即取消
            connectTimeoutTimer = scheduler.schedule(() -> {
                // 只检查 connectFutureRef，不检查 wsState
                CompletableFuture<Void> currentFuture = connectFutureRef.get();
                if (currentFuture == future) {
                    // 保存超时错误信息，供 handleClose 使用
                    connectionError = AgentException.connectionTimeout(timeout);
                    // 关闭连接，触发 onClose，让 handleClose 统一处理
                    if (ws != null) {
                        ws.close();
                    }
                }
            }, timeout, TimeUnit.MILLISECONDS);

            ws.connect();

        } catch (Exception e) {
            cancelConnectTimeoutTimer();
            setState(ConnectionState.DISCONNECTED);
            // 清理 connectFuture 引用
            connectFutureRef.compareAndSet(future, null);
            future.completeExceptionally(AgentException.connectionFailed("创建 WebSocket 失败", e));
        }
    }

    /**
     * 取消连接超时定时器
     * 在连接成功或失败时调用，避免定时器泄漏
     */
    private void cancelConnectTimeoutTimer() {
        if (connectTimeoutTimer != null) {
            connectTimeoutTimer.cancel(false);
            connectTimeoutTimer = null;
        }
    }

    /**
     * 发送消息
     *
     * @param payload 消息载荷
     * @throws AgentException 未连接或发送失败时抛出异常
     */
    public void send(@NonNull WSPayload payload) {
        if (!isConnected()) {
            log.error("WebSocket not connected, cannot send message, state: {}", wsState);
            throw AgentException.sendFailed("WebSocket 未连接");
        }

        try {
            String data = JSON.toJSONString(payload);
            log.debug("⬆️ [WS] Sending message, question_preview={}, type={}",
                    payload.getQuestion() != null
                            ? payload.getQuestion().substring(0, Math.min(20, payload.getQuestion().length())) + "..."
                            : "null",
                    payload.getOpenFlowUuid() != null ? "flow" : "text");
            if (ws == null || !ws.isOpen()) {
                throw AgentException.sendFailed("WebSocket 连接未打开");
            }
            ws.send(data);
        } catch (Exception e) {
            log.error("Failed to send message", e);
            throw AgentException.sendFailed("发送消息失败", e);
        }
    }

    /**
     * 发送心跳
     */
    public void sendHeartbeat() {
        if (!isConnected()) {
            return;
        }

        try {
            String heartbeat = PayloadBuilder.buildHeartbeatPayload();
            log.debug("Sending heartbeat");
            ws.send(heartbeat);
        } catch (Exception e) {
            log.debug("Failed to send heartbeat", e);
        }
    }

    /**
     * 关闭连接
     * 实现 AutoCloseable 接口，支持 try-with-resources 语法
     * 
     * 关闭连接并清理所有资源，包括 scheduler
     */
    @Override
    public void close() {
        // 关闭连接相关资源
        cancelConnectTimeoutTimer();
        clearReconnectTimer();
        stopHeartbeat();

        if (ws != null) {
            try {
                ws.close();
            } catch (Exception e) {
                log.debug("Error occurred while closing WebSocket connection", e);
            }
            ws = null;
        }

        setState(ConnectionState.CLOSED);
        // 清理所有处理器
        messageHandlers.clear();
        stateHandlers.clear();
        // 清理 connectFuture 引用和 connectStateRef
        connectFutureRef.set(null);
        connectStateRef.set(ConnectionState.CLOSED);

        // 关闭 scheduler 并等待终止
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 注册消息处理器
     *
     * @param handler 消息处理函数
     * @return 移除处理器的函数
     */
    @NonNull
    public Runnable onMessage(@NonNull WSMessageHandler handler) {
        messageHandlers.add(handler);
        return () -> messageHandlers.remove(handler);
    }

    /**
     * 移除消息处理器
     *
     * @param handler 要移除的消息处理器
     * @return 是否成功移除
     */
    public boolean removeMessageHandler(@NonNull WSMessageHandler handler) {
        return messageHandlers.remove(handler);
    }

    /**
     * 注册状态变化处理器
     *
     * @param handler 状态变化处理函数
     * @return 移除处理器的函数
     */
    @NonNull
    public Runnable onStateChange(@NonNull WSStateHandler handler) {
        stateHandlers.add(handler);
        return () -> stateHandlers.remove(handler);
    }

    /**
     * 处理收到的消息
     */
    private void handleMessage(@NonNull String message) {
        try {
            WSResponse response = JSON.parseObject(message, WSResponse.class);

            // 忽略心跳响应
            if (ResponseType.isType(response.getType(), ResponseType.HEARTBEAT)) {
                log.debug("Ignoring heartbeat response");
                return;
            }

            // 调用消息处理器
            messageHandlers.forEach(handler -> {
                try {
                    handler.handle(response);
                } catch (Exception e) {
                    log.error("Message handler execution error", e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to parse WebSocket message: {}", e.getMessage());
        }
    }

    /**
     * 处理连接关闭
     * 
     * 统一处理连接结束逻辑（成功或失败）：
     * - 完成连接 Future（如果未完成）
     * - 设置状态
     * - 触发重连（如果需要）
     */
    private void handleClose(int code, String reason, boolean remote) {
        boolean wasConnected = wsState == ConnectionState.CONNECTED;
        boolean autoReconnect = config.getWebsocket().getAutoReconnect() != null
                ? config.getWebsocket().getAutoReconnect()
                : true;
        log.debug("WebSocket connection closed, wasConnected: {}, autoReconnect: {}", wasConnected, autoReconnect);

        ws = null;
        stopHeartbeat();
        // 取消连接超时定时器（如果存在）
        cancelConnectTimeoutTimer();

        // 如果是主动关闭，不重连
        if (wsState == ConnectionState.CLOSED) {
            log.debug("Manually closed connection, not reconnecting");
            // 清理 connectFuture 引用（如果有正在进行的连接）
            CompletableFuture<Void> currentFuture = connectFutureRef.get();
            if (currentFuture != null && !currentFuture.isDone()) {
                connectFutureRef.compareAndSet(currentFuture, null);
                currentFuture.completeExceptionally(AgentException.connectionFailed("连接被手动关闭"));
            } else {
                connectFutureRef.set(null);
            }
            // 清理错误信息
            connectionError = null;
            return;
        }

        // 统一处理连接结束逻辑：完成 Future（如果未完成）
        CompletableFuture<Void> currentFuture = connectFutureRef.get();
        if (currentFuture != null && !currentFuture.isDone()) {
            connectFutureRef.compareAndSet(currentFuture, null);

            // 如果有保存的错误信息，使用它完成 Future
            if (connectionError != null) {
                // 检查是否是超时错误
                if (connectionError instanceof AgentException &&
                        connectionError.getMessage() != null &&
                        connectionError.getMessage().contains("连接超时")) {
                    currentFuture.completeExceptionally(connectionError);
                } else {
                    // 其他错误，构建错误详情
                    String errorDetails;
                    if (closeInfo != null) {
                        errorDetails = String.format("服务器返回 code: %d, reason: %s", closeInfo.code, closeInfo.reason);
                    } else {
                        String message = connectionError.getMessage();
                        if (message != null && message.contains("non-101")) {
                            errorDetails = "服务器返回了非 WebSocket 升级响应（可能是 URL 错误、服务不存在、或认证失败）";
                        } else {
                            errorDetails = "网络错误（可能是网络不通、服务器不可用、或被防火墙阻断）";
                        }
                    }
                    currentFuture.completeExceptionally(AgentException.connectionFailed(
                            "WebSocket 连接失败 - " + errorDetails, connectionError));
                }
            } else {
                // 没有错误信息，使用关闭信息完成 Future
                currentFuture.completeExceptionally(AgentException.connectionFailed(
                        String.format("连接被关闭: code=%d, reason=%s", code, reason)));
            }

            // 清理错误信息
            connectionError = null;
        }

        setState(ConnectionState.DISCONNECTED);

        // 自动重连：只在连接成功建立后断开时触发初始重连
        // 后续的重连由 scheduleReconnect() 的自驱循环处理
        if (wasConnected && autoReconnect) {
            scheduleReconnect();
        }
    }

    /**
     * 安排重连
     * 
     * 重连是一个自驱循环：
     * - 安排一次重连尝试
     * - 成功：重置计数
     * - 失败：再次调用 scheduleReconnect()（形成循环）
     */
    private void scheduleReconnect() {
        int maxRetries = config.getWebsocket().getMaxRetries() != null
                ? config.getWebsocket().getMaxRetries()
                : Constants.DEFAULT_MAX_RETRIES;

        int currentAttempts = reconnectAttempts.get();
        if (currentAttempts >= maxRetries) {
            log.debug("Reached maximum reconnection attempts, stopping reconnection, attempts: {}, maxRetries: {}",
                    currentAttempts, maxRetries);
            return;
        }

        log.debug("Scheduling reconnection, current attempts: {}, maxRetries: {}", currentAttempts, maxRetries);
        setState(ConnectionState.RECONNECTING);

        long interval = config.getWebsocket().getRetryInterval() != null
                ? config.getWebsocket().getRetryInterval()
                : Constants.DEFAULT_RETRY_INTERVAL;

        reconnectTimer = scheduler.schedule(() -> {
            // 清除定时器引用，允许下次重连
            reconnectTimer = null;

            int attempt = reconnectAttempts.incrementAndGet();
            log.debug("Starting reconnection attempt: {}", attempt);

            connect().whenComplete((result, ex) -> {
                if (ex == null) {
                    // 成功：重置计数
                    log.info("Reconnection successful, attempt: {}", attempt);
                    reconnectAttempts.set(0);
                } else {
                    // 失败：再次安排重连（自驱循环）
                    log.warn("Reconnection failed, attempt: {}, will retry", attempt, ex);
                    scheduleReconnect();
                }
            });

        }, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * 清除重连定时器
     */
    private void clearReconnectTimer() {
        if (reconnectTimer != null) {
            reconnectTimer.cancel(false);
            reconnectTimer = null;
        }
    }

    /**
     * 设置状态并通知处理器
     */
    private void setState(@NonNull ConnectionState state) {
        if (wsState != state) {
            log.debug("WebSocket state changed, from: {}, to: {}", wsState, state);
            wsState = state;

            // 同步更新 connectStateRef（用于状态 CAS）
            // 只在关键状态转换时更新
            if (state == ConnectionState.CONNECTING ||
                    state == ConnectionState.CONNECTED ||
                    state == ConnectionState.DISCONNECTED ||
                    state == ConnectionState.NOT_EXIST) {
                connectStateRef.set(state);
            }

            // 异步执行状态回调，避免阻塞和相互影响
            stateHandlers.forEach(handler -> callbackExecutor.execute(() -> {
                try {
                    handler.handle(state);
                } catch (Exception e) {
                    log.warn("State handler execution error", e);
                }
            }));
        }
    }

    /**
     * 启动心跳
     */
    private void startHeartbeat() {
        Long interval = config.getWebsocket().getHeartbeatInterval();

        // 如果设置为 0 或未定义，不启用心跳
        if (interval == null || interval <= 0) {
            log.debug("Heartbeat not enabled, interval: {}", interval);
            return;
        }

        log.debug("Starting heartbeat, interval: {}", interval);
        stopHeartbeat();

        heartbeatTimer = scheduler.scheduleAtFixedRate(this::sendHeartbeat, interval, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止心跳
     */
    private void stopHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel(false);
            heartbeatTimer = null;
        }
    }

}
