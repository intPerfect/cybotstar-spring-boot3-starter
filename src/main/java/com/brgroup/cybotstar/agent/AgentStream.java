package com.brgroup.cybotstar.agent;

import com.brgroup.cybotstar.agent.session.SessionContextManager;
import com.brgroup.cybotstar.stream.FastQueue;
import com.brgroup.cybotstar.stream.StreamManager;
import com.brgroup.cybotstar.stream.StreamState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

/**
 * Agent 流式对象
 * 提供流式数据的迭代访问，实现 Iterator 接口
 *
 * @author zhiyuan.xi
 */
public class AgentStream implements Iterator<String> {
    @NonNull
    private final String sessionId;
    @Nullable
    private final StreamManager streamManager;
    @Nullable
    private final SessionContextManager sessionManager;
    @Nullable
    private CompletableFuture<Void> completionFuture;
    @Nullable
    private StreamState state;
    private StreamManager.@Nullable StreamQueueItem nextItem;
    private boolean finished = false;
    @Nullable
    private String dialogId; // 保存 dialog_id，避免从已重置的 state 中获取

    public AgentStream(@NonNull String sessionId, @Nullable StreamManager streamManager, @Nullable SessionContextManager sessionManager) {
        this.sessionId = sessionId;
        this.streamManager = streamManager;
        this.sessionManager = sessionManager;
        if (sessionManager != null) {
            this.state = sessionManager.getStreamState(sessionId);
        }
    }

    /**
     * 设置完成 Future（由 stream() 方法调用）
     */
    public void setCompletionFuture(@NonNull CompletableFuture<Void> future) {
        this.completionFuture = future;
    }

    /**
     * 等待流完成
     */
    @NonNull
    public CompletableFuture<Void> done() {
        CompletableFuture<Void> future = completionFuture != null ? completionFuture
                : CompletableFuture.completedFuture(null);
        // 如果还没有 dialog_id，尝试从 state 中获取
        return future.thenRun(() -> {
            if (dialogId == null && state != null) {
                String stateDialogId = state.getStreamBuffer().getDialogId();
                if (stateDialogId != null) {
                    this.dialogId = stateDialogId;
                }
            }
        });
    }

    /**
     * 更新 dialog_id（在收到消息时调用）
     */
    public void updateDialogId(@Nullable String dialogId) {
        if (dialogId != null && this.dialogId == null) {
            this.dialogId = dialogId;
        }
    }

    /**
     * 获取对话 ID
     */
    @Nullable
    public String getDialogId() {
        // 优先使用保存的 dialog_id，如果没有则从 state 中获取
        if (dialogId != null) {
            return dialogId;
        }
        return state != null ? state.getStreamBuffer().getDialogId() : null;
    }

    @Override
    public boolean hasNext() {
        if (finished) {
            return false;
        }

        // 如果还没有获取下一个项，尝试从队列中获取
        if (nextItem == null && streamManager != null) {
            nextItem = waitForNextItem();
        }

        // 如果没有获取到项，说明流已结束
        if (nextItem == null) {
            return false;
        }

        // 检查是否是完成标记
        if (nextItem.isDone()) {
            // 在标记完成前，更新 dialog_id（如果存在）
            if (nextItem.getDialogId() != null) {
                updateDialogId(nextItem.getDialogId());
            }
            finished = true;
            return false;
        }

        // 检查是否有错误
        if (nextItem.getError() != null) {
            finished = true;
            throw new RuntimeException(nextItem.getError());
        }

        return true;
    }

    /**
     * 等待并获取下一个队列项
     * 使用 wait/notify 机制替代轮询，提高效率
     * 
     * 修复竞态条件：使用双重检查模式，确保在 wait() 前再次检查队列状态
     * 这样可以避免在 isEmpty() 检查和 wait() 之间数据被添加但未唤醒的情况
     *
     * @return 队列项，如果流已结束则返回 null
     */
    private StreamManager.@Nullable StreamQueueItem waitForNextItem() {
        if (streamManager == null) {
            return null;
        }

        // 获取队列引用（如果不存在则返回 null）
        FastQueue<StreamManager.StreamQueueItem> queue = streamManager.getQueue(sessionId);

        // 使用 synchronized 和 wait/notify 机制等待数据
        // 注意：FastQueue 内部使用 items 作为同步对象
        synchronized (queue.items) {
            // 双重检查模式：先尝试立即获取一次，避免不必要的等待
            if (!queue.isEmpty()) {
                StreamManager.StreamQueueItem item = queue.remove();
                updateDialogIdIfPresent(item);
                return item;
            }

            // 如果队列为空且流还在活跃，则等待数据到达
            // 使用双重检查模式修复竞态条件
            while (queue.isEmpty() && !finished && streamManager.isActive(sessionId)) {
                try {
                    // 在 wait() 前再次检查队列状态（双重检查）
                    // 避免在 isEmpty() 检查和 wait() 之间数据被添加但未唤醒
                    if (!queue.isEmpty()) {
                        StreamManager.StreamQueueItem item = queue.remove();
                        updateDialogIdIfPresent(item);
                        return item;
                    }
                    
                    // 等待数据到达，enqueue() 方法会调用 notifyWaiters() 唤醒
                    queue.items.wait();

                    // 被唤醒后，再次检查队列（双重检查）
                    if (!queue.isEmpty()) {
                        StreamManager.StreamQueueItem item = queue.remove();
                        updateDialogIdIfPresent(item);
                        return item;
                    }
                } catch (InterruptedException e) {
                    // 线程被中断，恢复中断状态并退出
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 如果流已结束或不再活跃，返回 null
            return null;
        }
    }

    /**
     * 如果项包含 dialog_id，则更新保存的 dialog_id
     */
    private void updateDialogIdIfPresent(StreamManager.@Nullable StreamQueueItem item) {
        if (item != null && item.getDialogId() != null) {
            updateDialogId(item.getDialogId());
        }
    }

    @Override
    @NonNull
    public String next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        String value = nextItem.getValue();
        nextItem = null;
        return value;
    }
}
