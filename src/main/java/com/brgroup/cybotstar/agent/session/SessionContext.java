package com.brgroup.cybotstar.agent.session;

import com.brgroup.cybotstar.connection.WebSocketConnection;
import com.brgroup.cybotstar.stream.StreamState;
import com.brgroup.cybotstar.agent.model.ExtendedSendOptions;
import com.brgroup.cybotstar.agent.model.MessageParam;
import com.brgroup.cybotstar.model.ws.WSPayload;
import com.brgroup.cybotstar.model.ws.WSResponse;
import lombok.Data;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 会话上下文类
 * 封装单个会话的所有状态和资源，便于统一管理和清理
 *
 * @author zhiyuan.xi
 */
@Data
public class SessionContext {
    /**
     * 会话 ID
     */
    @NonNull
    private final String sessionId;

    /**
     * WebSocket 连接
     */
    @Nullable
    private WebSocketConnection connection;

    /**
     * 流式状态
     */
    @Nullable
    private StreamState streamState;

    /**
     * 会话默认配置
     */
    @Nullable
    private ExtendedSendOptions config;

    /**
     * 对话历史列表
     * 存储该会话的所有消息，使用 MessageParam 格式
     * 每个 MessageParam 可以包含 dialogId（如果该消息有对应的 dialog_id）
     */
    @NonNull
    private final List<MessageParam> conversationHistory = new ArrayList<>();

    /**
     * 消息处理器（使用 volatile 确保多线程环境下的可见性）
     */
    private volatile WebSocketConnection.@Nullable WSMessageHandler messageHandler;

    public SessionContext(@NonNull String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 清理所有资源
     */
    public void clear() {
        // 移除消息处理器
        if (messageHandler != null && connection != null) {
            connection.removeMessageHandler(messageHandler);
            messageHandler = null;
        }
    }

    /**
     * 检查是否有活跃的流
     */
    public boolean hasActiveStream() {
        return streamState != null && streamState.getStreamConfig().getActiveStreamId() != null;
    }

    /**
     * 检查连接是否可用
     */
    public boolean isConnectionReady() {
        return connection != null && connection.isConnected();
    }

    /**
     * 获取对话历史
     * 返回的列表中的 MessageParam 不包含 dialogId 字段（序列化时会被忽略）
     * 返回副本，避免外部修改影响内部状态
     * 
     * @return 对话历史列表，可以直接用作 messageParams 参数
     */
    @NonNull
    public List<MessageParam> getHistoryMessages() {
        return new ArrayList<>(conversationHistory);
    }

    /**
     * 获取对话历史列表（内部使用）
     * 返回原始列表引用，用于内部添加消息
     * 
     * @return 对话历史列表的原始引用
     */
    @NonNull
    public List<MessageParam> getConversationHistory() {
        return conversationHistory;
    }

    /**
     * 添加消息到对话历史
     * 
     * @param message 要添加的消息
     */
    public void addHistory(@NonNull MessageParam message) {
        conversationHistory.add(message);
    }

    /**
     * Agent 回调集合
     */
    @Data
    public static class AgentCallbacks {
        @Nullable
        private Consumer<String> onChunk;
        @Nullable
        private Consumer<String> onComplete;
        @Nullable
        private Consumer<Throwable> onError;
        @Nullable
        private Runnable onConnected;
        @Nullable
        private Runnable onDisconnected;
        @Nullable
        private Consumer<Integer> onReconnecting;
        @Nullable
        private BiConsumer<String, String> onMessage;
        @Nullable
        private Consumer<String> onReasoning;
        @Nullable
        private Consumer<WSResponse> onRawResponse;
        @Nullable
        private Consumer<WSPayload> onRawRequest;
    }
}
