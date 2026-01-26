package com.brgroup.cybotstar.connection;

import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.model.common.ConnectionState;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * 单连接管理器
 *
 * 负责管理单个 WebSocket 连接的生命周期，包括创建、复用、断开等。
 * 相比 ConnectionManager，这个版本只管理一个连接，适合 FlowClient 这种不需要多会话的场景。
 * 
 * 实现了 IConnectionManager 接口，所有 sessionId 参数都会被忽略，统一使用单个连接。
 * 同时保留了无参数便捷方法以保持向后兼容。
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class SingleConnectionManager implements IConnectionManager {

    /**
     * 固定会话 ID（单连接管理器忽略所有 sessionId，统一使用此值）
     */
    private static final String SINGLE_SESSION_ID = "";

    /**
     * 连接实例
     */
    @Nullable
    private WebSocketConnection connection;

    /**
     * 配置
     */
    @NonNull
    private final AgentConfig config;

    /**
     * 连接状态变化回调集合（IConnectionManager 接口的回调）
     */
    private final Set<IConnectionManager.ConnectionStateCallback> stateChangeCallbacks = ConcurrentHashMap.newKeySet();

    /**
     * 回调执行器（用于异步执行状态回调，避免阻塞）
     */
    private final Executor callbackExecutor = ForkJoinPool.commonPool();

    public SingleConnectionManager(@NonNull AgentConfig config) {
        this.config = config;
    }

    /**
     * 获取连接（IConnectionManager 接口实现）
     * 忽略 sessionId 参数，始终返回同一个连接
     *
     * @param sessionId 会话 ID（被忽略）
     * @return WebSocket 连接实例
     */
    @Override
    @NonNull
    public WebSocketConnection getConnection(@NonNull String sessionId) {
        return getConnection();
    }

    /**
     * 获取连接（便捷方法，向后兼容）
     *
     * 如果连接不存在，则创建新的连接；否则返回现有连接。
     *
     * @return WebSocket 连接实例
     */
    @NonNull
    public WebSocketConnection getConnection() {
        if (connection == null) {
            log.debug("Creating new WebSocket connection");
            connection = new WebSocketConnection(config);
            // 监听连接状态变化
            connection.onStateChange(state -> notifyStateChange(SINGLE_SESSION_ID, state));
        }
        return connection;
    }

    /**
     * 获取连接（便捷方法，向后兼容）
     *
     * @return WebSocket 连接实例，不存在则返回 null
     */
    @Nullable
    public WebSocketConnection get() {
        return connection;
    }

    /**
     * 检查连接是否存在（IConnectionManager 接口实现）
     * 忽略 sessionId 参数
     *
     * @param sessionId 会话 ID（被忽略）
     * @return 是否存在
     */
    @Override
    public boolean has(@NonNull String sessionId) {
        return hasConnection();
    }

    /**
     * 检查连接是否存在（便捷方法，向后兼容）
     *
     * @return 是否存在
     */
    public boolean hasConnection() {
        return connection != null;
    }

    /**
     * 检查连接是否已连接（IConnectionManager 接口实现）
     * 忽略 sessionId 参数
     *
     * @param sessionId 会话 ID（被忽略）
     * @return 是否已连接
     */
    @Override
    public boolean isConnected(@NonNull String sessionId) {
        return isConnected();
    }

    /**
     * 检查连接是否已连接（便捷方法，向后兼容）
     *
     * @return 是否已连接
     */
    public boolean isConnected() {
        return connection != null && connection.isConnected();
    }

    /**
     * 获取连接状态（IConnectionManager 接口实现）
     * 忽略 sessionId 参数
     *
     * @param sessionId 会话 ID（被忽略）
     * @return 连接状态，不存在则返回 NOT_EXIST
     */
    @Override
    @NonNull
    public ConnectionState getState(@NonNull String sessionId) {
        return getState();
    }

    /**
     * 获取连接状态（便捷方法，向后兼容）
     *
     * @return 连接状态，不存在则返回 NOT_EXIST
     */
    @NonNull
    public ConnectionState getState() {
        return connection != null ? connection.getState() : ConnectionState.NOT_EXIST;
    }

    /**
     * 建立连接（IConnectionManager 接口实现）
     * 忽略 sessionId 参数
     *
     * @param sessionId 会话 ID（被忽略）
     * @return CompletableFuture，连接成功时 complete
     */
    @Override
    @NonNull
    public CompletableFuture<Void> connect(@NonNull String sessionId) {
        return connect();
    }

    /**
     * 建立连接（便捷方法，向后兼容）
     *
     * @return CompletableFuture，连接成功时 complete
     */
    @NonNull
    public CompletableFuture<Void> connect() {
        log.info("Establishing WebSocket connection");
        WebSocketConnection conn = getConnection();
        return conn.connect().thenRun(() -> {
            log.info("WebSocket connection established successfully");
        });
    }

    /**
     * 断开连接（IConnectionManager 接口实现）
     * 忽略 sessionId 参数
     *
     * @param sessionId 会话 ID（被忽略）
     */
    @Override
    public void disconnect(@NonNull String sessionId) {
        disconnect();
    }

    /**
     * 断开连接（便捷方法，向后兼容）
     */
    public void disconnect() {
        log.debug("Disconnecting WebSocket connection");
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    /**
     * 注册消息处理器
     *
     * @param handler 消息处理函数
     * @return 移除处理器的函数
     */
    @NonNull
    public Runnable onMessage(WebSocketConnection.@NonNull WSMessageHandler handler) {
        WebSocketConnection conn = getConnection();
        return conn.onMessage(handler);
    }

    /**
     * 注册状态变化回调（IConnectionManager 接口实现）
     *
     * @param callback 回调函数
     */
    @Override
    public void registerStateChangeCallback(IConnectionManager.@NonNull ConnectionStateCallback callback) {
        stateChangeCallbacks.add(callback);
    }

    /**
     * 通知状态变化
     * 
     * 异步执行回调，避免某个回调卡死导致所有回调阻塞
     *
     * @param sessionId 会话 ID
     * @param state     连接状态
     */
    private void notifyStateChange(@NonNull String sessionId, @NonNull ConnectionState state) {
        stateChangeCallbacks.forEach(callback -> callbackExecutor.execute(() -> {
            try {
                callback.onStateChange(sessionId, state);
            } catch (Exception e) {
                log.warn("State change callback error", e);
            }
        }));
    }

    /**
     * 关闭所有连接并清理资源（IConnectionManager 接口实现）
     */
    @Override
    public void shutdown() {
        log.debug("Closing all connections and cleaning up resources");
        disconnect();
        stateChangeCallbacks.clear();
    }
}
