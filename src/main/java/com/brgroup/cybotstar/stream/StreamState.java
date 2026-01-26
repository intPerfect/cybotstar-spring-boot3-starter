package com.brgroup.cybotstar.stream;

import com.brgroup.cybotstar.model.common.SessionState;
import lombok.Data;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * 流式状态
 *
 * @author zhiyuan.xi
 */
@Data
public class StreamState {
    /**
     * 会话状态
     */
    @NonNull
    private SessionState sessionState = SessionState.IDLE;

    /**
     * 发送状态
     */
    @NonNull
    private SendState sendState = new SendState();

    /**
     * 流配置
     */
    @NonNull
    private StreamConfig streamConfig = new StreamConfig();

    /**
     * 回调函数
     */
    @NonNull
    private StreamCallbacks callbacks = new StreamCallbacks();

    /**
     * Promise 处理器
     */
    @NonNull
    private PromiseHandlers promiseHandlers = new PromiseHandlers();

    /**
     * 流缓冲区
     */
    @NonNull
    private StreamBuffer streamBuffer = new StreamBuffer();

    /**
     * 响应超时定时器
     */
    @Nullable
    private ScheduledFuture<?> timeoutId;

    /**
     * 响应超时时间（毫秒）
     */
    private long timeout = 0;

    /**
     * 刷新超时定时器的函数
     */
    @Nullable
    private Runnable refreshTimeout;

    /**
     * 发送状态
     */
    @Data
    public static class SendState {
        /**
         * 是否正在发送
         */
        private boolean sending = false;

        /**
         * 上次请求结束时间
         */
        private long lastRequestEndTime = 0;
    }

    /**
     * 流配置
     */
    @Data
    public static class StreamConfig {
        /**
         * 当前节点 ID
         */
        @Nullable
        private String currentNodeId;

        /**
         * 是否为流式模式
         */
        private boolean currentStream = false;

        /**
         * 活动流 ID
         */
        @Nullable
        private String activeStreamId;
    }

    /**
     * 回调函数集合
     */
    @Data
    public static class StreamCallbacks {
        /**
         * 当前 chunk 回调
         */
        @Nullable
        private Consumer<String> currentOnChunk;

        /**
         * 当前完成回调
         */
        @Nullable
        private Consumer<String> currentOnComplete;
    }

    /**
     * Promise 处理器集合
     */
    @Data
    public static class PromiseHandlers {
        /**
         * 待处理的 resolve 函数
         */
        @Nullable
        private Consumer<String> pendingResolve;

        /**
         * 待处理的 reject 函数
         */
        @Nullable
        private Consumer<Throwable> pendingReject;

        /**
         * 流完成 Promise 的 resolve 函数
         */
        @Nullable
        private Runnable streamCompletionResolve;

        /**
         * 流完成 Promise 的 reject 函数
         */
        @Nullable
        private Consumer<Throwable> streamCompletionReject;
    }

    /**
     * 流缓冲区
     */
    @Data
    public static class StreamBuffer {
        /**
         * 流式缓冲区
         */
        @NonNull
        private StringBuilder buffer = new StringBuilder();

        /**
         * 流式消息 ID
         */
        @NonNull
        private String msgId = "";

        /**
         * 对话 ID
         */
        @Nullable
        private String dialogId;

        /**
         * 当前请求的问题
         */
        @Nullable
        private String question;
    }
}

