package com.brgroup.cybotstar.stream;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流式管理器
 * 负责管理流式响应的队列、信号和状态
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class StreamManager {

    /**
     * 流队列池（按 sessionId 索引）
     * 使用高性能队列实现，避免 O(n) 的 remove 操作
     */
    private final Map<String, FastQueue<StreamQueueItem>> streamQueues = new ConcurrentHashMap<>();

    /**
     * 活跃流 ID 池（按 sessionId 索引）
     */
    private final Set<String> activeStreamIds = ConcurrentHashMap.newKeySet();

    /**
     * 流队列项
     */
    @lombok.Data
    public static class StreamQueueItem {
        @Nullable
        private String value;
        private boolean done;
        @Nullable
        private Throwable error;
        @Nullable
        private String dialogId; // 保存 dialog_id
    }

    /**
     * 准备流（初始化队列和标记活跃状态）
     *
     * @param sessionId 会话 ID
     */
    public void prepareStream(@NonNull String sessionId) {
        log.debug("Preparing stream queue, sessionId: {}", sessionId);
        streamQueues.put(sessionId, new FastQueue<>());
        activeStreamIds.add(sessionId);
    }

    /**
     * 获取流队列
     * 返回高性能队列引用，内部已实现线程安全
     *
     * @param sessionId 会话 ID
     * @return 流队列
     */
    @NonNull
    public FastQueue<StreamQueueItem> getQueue(@NonNull String sessionId) {
        // computeIfAbsent 是线程安全的
        return streamQueues.computeIfAbsent(sessionId, k -> new FastQueue<>());
    }

    /**
     * 推送数据到流队列
     *
     * @param sessionId 会话 ID
     * @param item      流队列项
     */
    public void enqueue(@NonNull String sessionId, @NonNull StreamQueueItem item) {
        FastQueue<StreamQueueItem> queue = getQueue(sessionId);
        queue.add(item);
        log.debug("Adding stream data, sessionId: {}, done: {}", sessionId, item.isDone());
        queue.notifyWaiters(); // 通知所有等待者
    }

    /**
     * 从流队列取出数据
     *
     * @param sessionId 会话 ID
     * @return 流队列项，队列为空则返回 null
     */
    @Nullable
    public StreamQueueItem dequeue(@NonNull String sessionId) {
        FastQueue<StreamQueueItem> queue = streamQueues.get(sessionId);
        if (queue == null) {
            return null;
        }
        return queue.remove();
    }

    /**
     * 检查队列是否为空
     *
     * @param sessionId 会话 ID
     * @return 是否为空
     */
    public boolean isEmpty(@NonNull String sessionId) {
        FastQueue<StreamQueueItem> queue = streamQueues.get(sessionId);
        return queue == null || queue.isEmpty();
    }

    /**
     * 检查流是否活跃
     *
     * @param sessionId 会话 ID
     * @return 是否活跃
     */
    public boolean isActive(@NonNull String sessionId) {
        return activeStreamIds.contains(sessionId);
    }

    /**
     * 清理流资源
     * 确保所有等待的线程都能被正确唤醒，避免死锁
     *
     * @param sessionId 会话 ID
     */
    public void cleanup(@NonNull String sessionId) {
        log.debug("Cleaning up stream resources, sessionId: {}", sessionId);
        FastQueue<StreamQueueItem> queue = streamQueues.get(sessionId);
        if (queue != null) {
            // 先唤醒所有等待的线程，避免死锁
            queue.notifyWaiters();
            // 然后清空队列
            queue.clear();
            // 从映射中移除
            streamQueues.remove(sessionId);
        }
        // 确保从活跃流集合中移除
        activeStreamIds.remove(sessionId);
    }

    /**
     * 清理所有流资源
     * 确保所有等待的线程都能被正确唤醒
     */
    public void cleanupAll() {
        log.debug("Cleaning up all stream resources, count: {}", activeStreamIds.size());
        // 先唤醒所有队列中等待的线程
        streamQueues.values().forEach(FastQueue::notifyWaiters);
        // 然后清空所有资源
        streamQueues.clear();
        activeStreamIds.clear();
    }
}
