package com.brgroup.cybotstar.connection;

import com.brgroup.cybotstar.model.common.ConnectionState;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

/**
 * 连接管理器接口
 * 定义连接管理器的核心功能，包括连接的创建、获取、断开等操作
 *
 * @author zhiyuan.xi
 */
public interface IConnectionManager {

    /**
     * 连接状态变化回调接口
     */
    @FunctionalInterface
    interface ConnectionStateCallback {
        void onStateChange(@NonNull String sessionId, @NonNull ConnectionState state);
    }

    /**
     * 注册连接状态变化回调
     *
     * @param callback 回调函数
     */
    void registerStateChangeCallback(@NonNull ConnectionStateCallback callback);

    /**
     * 获取连接
     * 如果连接不存在，则创建新的连接；否则返回现有连接
     *
     * @param sessionId 会话 ID
     * @return WebSocket 连接实例
     */
    @NonNull
    WebSocketConnection getConnection(@NonNull String sessionId);

    /**
     * 检查连接是否存在
     *
     * @param sessionId 会话 ID
     * @return 是否存在
     */
    boolean has(@NonNull String sessionId);

    /**
     * 检查连接是否已连接
     *
     * @param sessionId 会话 ID
     * @return 连接状态
     */
    boolean isConnected(@NonNull String sessionId);

    /**
     * 获取连接状态
     *
     * @param sessionId 会话 ID
     * @return 连接状态，不存在则返回 NOT_EXIST
     */
    @NonNull
    ConnectionState getState(@NonNull String sessionId);

    /**
     * 建立连接
     *
     * @param sessionId 会话 ID
     * @return CompletableFuture，连接成功时 complete
     */
    @NonNull
    CompletableFuture<Void> connect(@NonNull String sessionId);

    /**
     * 断开连接
     *
     * @param sessionId 会话 ID
     */
    void disconnect(@NonNull String sessionId);

    /**
     * 关闭所有连接并清理资源
     */
    void shutdown();
}

