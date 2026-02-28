package com.brgroup.cybotstar.util;

/**
 * CybotStar 常量定义
 *
 * @author zhiyuan.xi
 */
public final class CybotStarConstants {

    private CybotStarConstants() {
        // 工具类，禁止实例化
    }

    // ============================================================================
    // 请求相关
    // ============================================================================

    /**
     * 请求之间的最小间隔（毫秒）
     */
    public static final long MIN_REQUEST_INTERVAL = 300;

    /**
     * 默认响应超时时间（毫秒）
     */
    public static final long DEFAULT_RESPONSE_TIMEOUT = 30000;

    // ============================================================================
    // 会话相关
    // ============================================================================

    /**
     * 默认 session ID
     */
    public static final String DEFAULT_SESSION_ID = "default";

    /**
     * 默认会话 ID 显示名称（用于日志）
     */
    public static final String DEFAULT_SESSION_DISPLAY_NAME = "default-session";

    // ============================================================================
    // WebSocket 相关
    // ============================================================================

    /**
     * WebSocket 默认连接超时时间（毫秒）
     */
    public static final long DEFAULT_WS_TIMEOUT = 30000;

    /**
     * 默认重连间隔时间（毫秒）
     */
    public static final long DEFAULT_RETRY_INTERVAL = 1000;

    /**
     * 默认最大重连次数
     */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 最大重连退避时间（毫秒）
     */
    public static final long MAX_RETRY_BACKOFF = 30000;

    // ============================================================================
    // 消息角色相关
    // ============================================================================

    /**
     * 用户消息角色
     */
    public static final String ROLE_USER = "user";

    /**
     * 助手消息角色
     */
    public static final String ROLE_ASSISTANT = "assistant";

    /**
     * 系统消息角色
     */
    public static final String ROLE_SYSTEM = "system";

    // ============================================================================
    // 字段名称常量
    // ============================================================================

    /**
     * 数据字段名
     */
    public static final String FIELD_DATA = "data";

    /**
     * 消息字段名
     */
    public static final String FIELD_MESSAGE = "message";

    /**
     * 代码字段名
     */
    public static final String FIELD_CODE = "code";

    /**
     * 类型字段名
     */
    public static final String FIELD_TYPE = "type";

    /**
     * 内容字段名
     */
    public static final String FIELD_CONTENT = "content";

    // ============================================================================
    // 缓存相关
    // ============================================================================

    /**
     * 连接缓存最大容量
     */
    public static final int CONNECTION_CACHE_MAX_SIZE = 1000;

    /**
     * 连接缓存过期时间（分钟）
     */
    public static final int CONNECTION_CACHE_EXPIRE_MINUTES = 30;

    // ============================================================================
    // 流式输出相关
    // ============================================================================

    /**
     * 流式输出初始等待时间（毫秒）
     */
    public static final long STREAM_WAIT_INITIAL_DELAY = 30000;

    /**
     * 流式输出轮询间隔（毫秒）
     */
    public static final long STREAM_WAIT_POLL_INTERVAL = 500;

    // ============================================================================
    // 示例相关
    // ============================================================================

    /**
     * 会话示例延迟时间（毫秒）
     */
    public static final long SESSION_EXAMPLE_DELAY = 1000;
}
