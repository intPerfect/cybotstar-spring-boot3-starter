package com.brgroup.cybotstar.connection;

import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.model.common.ConnectionState;
import com.brgroup.cybotstar.util.CybotStarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * 连接管理器
 * 负责管理 WebSocket 连接池的生命周期，包括创建、复用、断开等
 * 
 * 实现 AutoCloseable 接口，支持 try-with-resources 语法
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class ConnectionManager implements IConnectionManager, AutoCloseable {

    /**
     * 连接池
     */
    private final Map<String, WebSocketConnection> connections = new ConcurrentHashMap<>();

    /**
     * 配置
     */
    @NonNull
    private final AgentConfig config;

    /**
     * 连接状态变化回调集合
     */
    private final Set<IConnectionManager.ConnectionStateCallback> stateChangeCallbacks = ConcurrentHashMap.newKeySet();

    /**
     * 回调执行器（用于异步执行状态回调，避免阻塞）
     */
    private final Executor callbackExecutor = ForkJoinPool.commonPool();

    public ConnectionManager(@NonNull AgentConfig config) {
        this.config = config;
    }

    /**
     * 获取连接
     * 如果连接不存在，则创建新的连接；否则返回现有连接
     *
     * @param sessionId 会话 ID
     * @return WebSocket 连接实例
     */
    @Override
    @NonNull
    public WebSocketConnection getConnection(@NonNull String sessionId) {
        return connections.computeIfAbsent(sessionId, id -> {
            log.debug("Creating new WebSocket connection, sessionId: {}", CybotStarUtils.formatSessionId(id));
            WebSocketConnection connection = new WebSocketConnection(config);
            // 监听连接状态变化
            connection.onStateChange(state -> notifyStateChange(id, state));
            return connection;
        });
    }

    /**
     * 检查连接是否存在
     *
     * @param sessionId 会话 ID
     * @return 是否存在
     */
    @Override
    public boolean has(@NonNull String sessionId) {
        return connections.containsKey(sessionId);
    }

    /**
     * 检查连接是否已连接
     *
     * @param sessionId 会话 ID
     * @return 连接状态
     */
    @Override
    public boolean isConnected(@NonNull String sessionId) {
        WebSocketConnection connection = connections.get(sessionId);
        return connection != null && connection.isConnected();
    }

    /**
     * 获取连接状态
     * 
     * 注意：NOT_EXIST 表示"不受 Manager 管理"，不等价于连接生命周期状态。
     * 如果连接已从 Manager 中移除，即使连接本身可能仍处于 CLOSED 状态，也会返回 NOT_EXIST。
     *
     * @param sessionId 会话 ID
     * @return 连接状态，不存在则返回 NOT_EXIST
     */
    @Override
    @NonNull
    public ConnectionState getState(@NonNull String sessionId) {
        WebSocketConnection connection = connections.get(sessionId);
        return connection != null ? connection.getState() : ConnectionState.NOT_EXIST;
    }

    /**
     * 建立连接
     *
     * @param sessionId 会话 ID
     * @return CompletableFuture，连接成功时 complete
     */
    @Override
    @NonNull
    public CompletableFuture<Void> connect(@NonNull String sessionId) {
        log.debug("Establishing WebSocket connection, sessionId: {}", CybotStarUtils.formatSessionId(sessionId));
        WebSocketConnection connection = getConnection(sessionId);
        return connection.connect().thenRun(() -> {
            log.debug("WebSocket connection established successfully, sessionId: {}",
                    CybotStarUtils.formatSessionId(sessionId));
        });
    }

    /**
     * 断开连接
     * 
     * 先关闭连接（触发状态回调），再从 Manager 中移除。
     * 这样可以保证状态回调触发时，连接仍在 Manager 管理范围内。
     *
     * @param sessionId 会话 ID
     */
    @Override
    public void disconnect(@NonNull String sessionId) {
        log.debug("Disconnecting WebSocket connection, sessionId: {}", CybotStarUtils.formatSessionId(sessionId));
        WebSocketConnection connection = connections.get(sessionId);
        if (connection != null) {
            // 先关闭连接，触发状态回调（此时连接仍在 Manager 中）
            connection.close();
            // 然后从 Manager 中移除
            connections.remove(sessionId);
        }
    }

    /**
     * 注册连接状态变化回调
     *
     * @param callback 回调函数
     */
    @Override
    public void registerStateChangeCallback(@NonNull ConnectionStateCallback callback) {
        stateChangeCallbacks.add(callback);
    }

    /**
     * 通知状态变化
     * 
     * 异步执行回调，避免某个回调卡死导致所有回调阻塞
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
     * 关闭所有连接并清理资源
     * 
     * 实现 AutoCloseable 接口，支持 try-with-resources 语法
     */
    @Override
    public void close() {
        log.debug("Disconnecting all WebSocket connections");
        connections.forEach((sessionId, connection) -> {
            try {
                // 使用 close() 确保完全清理资源（包括 scheduler）
                connection.close();
            } catch (Exception e) {
                log.debug("Error occurred while closing connection, sessionId: {}",
                        CybotStarUtils.formatSessionId(sessionId), e);
            }
        });
        connections.clear();
        stateChangeCallbacks.clear();
    }

    /**
     * 关闭所有连接并清理资源
     * 
     * @deprecated 使用 {@link #close()} 代替
     */
    @Override
    @Deprecated
    public void shutdown() {
        close();
    }
}
