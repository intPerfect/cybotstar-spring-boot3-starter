package com.brgroup.cybotstar.util;

/**
 * 常量定义
 *
 * @author zhiyuan.xi
 */
public class Constants {
    /**
     * 请求之间的最小间隔（毫秒）
     */
    public static final long MIN_REQUEST_INTERVAL = 300;

    /**
     * 默认响应超时时间（毫秒）
     */
    public static final long DEFAULT_RESPONSE_TIMEOUT = 10000;

    /**
     * 默认 session ID
     */
    public static final String DEFAULT_SESSION_ID = "default";

    /**
     * WebSocket 默认连接超时时间（毫秒）
     */
    public static final long DEFAULT_WS_TIMEOUT = 5000;

    /**
     * 默认重连间隔时间（毫秒）
     */
    public static final long DEFAULT_RETRY_INTERVAL = 1000;

    /**
     * 默认最大重连次数
     */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 默认会话 ID 显示名称（用于日志）
     */
    public static final String DEFAULT_SESSION_DISPLAY_NAME = "default-session";

    /**
     * 流式输出初始等待时间（毫秒）
     */
    public static final long STREAM_WAIT_INITIAL_DELAY = 3000;

    /**
     * 流式输出轮询间隔（毫秒）
     */
    public static final long STREAM_WAIT_POLL_INTERVAL = 500;

    /**
     * 会话示例延迟时间（毫秒）
     */
    public static final long SESSION_EXAMPLE_DELAY = 1000;

    private Constants() {
        // 工具类，禁止实例化
    }
}

