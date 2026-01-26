package com.brgroup.cybotstar.agent.session;

import com.brgroup.cybotstar.model.common.SessionState;
import com.brgroup.cybotstar.stream.StreamState;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话上下文管理器类
 * 管理所有会话上下文的生命周期，提供统一的状态访问接口
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class SessionContextManager {

    /**
     * 会话上下文映射表
     */
    private final Map<String, SessionContext> contexts = new ConcurrentHashMap<>();

    /**
     * 获取会话上下文
     * 如果会话上下文不存在，则创建新的上下文；否则返回现有上下文
     *
     * @param sessionId 会话 ID
     * @return 会话上下文对象
     */
    @NonNull
    public SessionContext getSession(@NonNull String sessionId) {
        return contexts.computeIfAbsent(sessionId, SessionContext::new);
    }

    /**
     * 获取会话上下文
     *
     * @param sessionId 会话 ID
     * @return 会话上下文对象，如果不存在则返回 null
     */
    @Nullable
    public SessionContext get(@NonNull String sessionId) {
        return contexts.get(sessionId);
    }

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话 ID
     * @return 如果会话上下文存在，返回 true；否则返回 false
     */
    public boolean has(@NonNull String sessionId) {
        return contexts.containsKey(sessionId);
    }

    /**
     * 移除会话上下文
     *
     * @param sessionId 会话 ID
     */
    public void remove(@NonNull String sessionId) {
        SessionContext context = contexts.remove(sessionId);
        if (context != null) {
            context.clear();
        }
    }

    /**
     * 获取所有会话 ID
     *
     * @return 所有会话 ID 的列表
     */
    @NonNull
    public List<String> getAllSessionIds() {
        return new ArrayList<>(contexts.keySet());
    }

    /**
     * 清空所有会话
     */
    public void clear() {
        contexts.values().forEach(SessionContext::clear);
        contexts.clear();
    }

    /**
     * 获取会话数量
     *
     * @return 当前管理的会话数量
     */
    public int size() {
        return contexts.size();
    }

    /**
     * 获取流式状态
     * 如果流式状态不存在，则创建新的状态；否则返回现有状态
     *
     * @param sessionId 会话 ID
     * @return 流式状态对象
     */
    @NonNull
    public StreamState getStreamState(@NonNull String sessionId) {
        SessionContext context = getSession(sessionId);
        if (context.getStreamState() == null) {
            context.setStreamState(new StreamState());
        }
        return context.getStreamState();
    }

    /**
     * 重置所有流式状态
     */
    public void resetAllState() {
        contexts.values().forEach(context -> {
            if (context.getStreamState() != null) {
                context.setStreamState(new StreamState());
            }
        });
    }

    /**
     * 重置指定会话的所有状态
     *
     * @param sessionId 会话 ID
     */
    public void resetAllState(@NonNull String sessionId) {
        SessionContext context = contexts.get(sessionId);
        if (context != null && context.getStreamState() != null) {
            StreamState state = context.getStreamState();
            // 清理超时定时器
            if (state.getTimeoutId() != null) {
                state.getTimeoutId().cancel(false);
                state.setTimeoutId(null);
            }
            // 清理刷新超时函数
            state.setRefreshTimeout(null);
            // 重置所有状态字段
            state.setSessionState(SessionState.IDLE);
            state.getSendState().setSending(false);
            state.getSendState().setLastRequestEndTime(0);
            state.getStreamBuffer().getBuffer().setLength(0);
            state.getStreamBuffer().setMsgId("");
            state.getStreamBuffer().setDialogId(null);
            state.getStreamBuffer().setQuestion(null);
            state.getPromiseHandlers().setPendingResolve(null);
            state.getPromiseHandlers().setPendingReject(null);
            state.getPromiseHandlers().setStreamCompletionResolve(null);
            state.getPromiseHandlers().setStreamCompletionReject(null);
            state.getCallbacks().setCurrentOnChunk(null);
            state.getCallbacks().setCurrentOnComplete(null);
            state.getStreamConfig().setCurrentStream(false);
            state.getStreamConfig().setCurrentNodeId(null);
            state.getStreamConfig().setActiveStreamId(null);
            state.setTimeout(0);
        }
    }

    /**
     * 获取会话状态
     *
     * @param sessionId 会话 ID
     * @return 会话状态枚举值
     */
    @NonNull
    public SessionState getSessionState(@NonNull String sessionId) {
        SessionContext context = contexts.get(sessionId);
        if (context != null && context.getStreamState() != null) {
            return context.getStreamState().getSessionState();
        }
        return SessionState.IDLE;
    }
}

