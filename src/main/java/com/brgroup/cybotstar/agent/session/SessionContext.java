package com.brgroup.cybotstar.agent.session;

import com.brgroup.cybotstar.agent.model.request.MessageParam;
import com.brgroup.cybotstar.core.connection.WebSocketConnection;
import com.brgroup.cybotstar.agent.handler.ReactiveMessageHandler;
import com.brgroup.cybotstar.core.model.ws.WSResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 响应式会话上下文
 * 管理单个会话的所有状态和资源
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class SessionContext {

    @NonNull
    private final String sessionId;

    @NonNull
    private final WebSocketConnection connection;

    @NonNull
    private final ReactiveMessageHandler messageHandler;

    // 对话历史（使用 AtomicReference 保证线程安全）
    private final AtomicReference<List<MessageParam>> historyRef =
            new AtomicReference<>(new ArrayList<>());

    public SessionContext(
            @NonNull String sessionId,
            @NonNull WebSocketConnection connection) {
        this.sessionId = sessionId;
        this.connection = connection;
        this.messageHandler = new ReactiveMessageHandler();
    }

    /**
     * 获取会话 ID
     */
    @NonNull
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取连接
     */
    @NonNull
    public WebSocketConnection getConnection() {
        return connection;
    }

    /**
     * 获取原始消息流
     */
    @NonNull
    public Flux<WSResponse> messageStream() {
        return connection.messages();
    }

    /**
     * 获取消息事件流
     */
    @NonNull
    public Flux<ReactiveMessageHandler.MessageEvent> eventStream() {
        return messageHandler.handle(connection.messages());
    }

    /**
     * 获取流式 chunk 流
     */
    @NonNull
    public Flux<String> chunkStream() {
        return messageHandler.extractStreamContent(connection.messages());
    }

    /**
     * 等待完成信号
     */
    @NonNull
    public Mono<String> waitForCompletion() {
        return messageHandler.waitForCompletion(connection.messages());
    }

    /**
     * 获取对话历史
     */
    @NonNull
    public List<MessageParam> getHistory() {
        return new ArrayList<>(historyRef.get());
    }

    /**
     * 添加历史消息
     */
    @NonNull
    public Mono<Void> addHistory(@NonNull MessageParam message) {
        return Mono.fromRunnable(() -> {
            historyRef.updateAndGet(list -> {
                List<MessageParam> newList = new ArrayList<>(list);
                newList.add(message);
                return newList;
            });
            log.debug("Added history message, sessionId: {}, role: {}", sessionId, message.getRole());
        });
    }

    /**
     * 添加多条历史消息
     */
    @NonNull
    public Mono<Void> addHistory(@NonNull List<MessageParam> messages) {
        return Mono.fromRunnable(() -> {
            historyRef.updateAndGet(list -> {
                List<MessageParam> newList = new ArrayList<>(list);
                newList.addAll(messages);
                return newList;
            });
            log.debug("Added {} history messages, sessionId: {}", messages.size(), sessionId);
        });
    }

    /**
     * 清空历史消息
     */
    @NonNull
    public Mono<Void> clearHistory() {
        return Mono.fromRunnable(() -> {
            historyRef.set(new ArrayList<>());
            log.debug("Cleared history, sessionId: {}", sessionId);
        });
    }

    /**
     * 关闭会话
     */
    public void close() {
        connection.close();
        log.debug("Session closed, sessionId: {}", sessionId);
    }
}
