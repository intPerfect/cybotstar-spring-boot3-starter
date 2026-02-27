package com.brgroup.cybotstar.util;

import cn.hutool.core.bean.BeanUtil;
import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.agent.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CybotStar 工具类
 * <p>
 * 提供配置验证、ID生成、格式化、时间处理等通用工具方法。
 *
 * @author zhiyuan.xi
 */
@Slf4j
public final class CybotStarUtils {

    private CybotStarUtils() {
        // 工具类，禁止实例化
    }

    // ============================================================================
    // 配置验证
    // ============================================================================

    /**
     * 验证配置完整性
     *
     * @param config 客户端配置
     * @throws AgentException 配置无效时抛出异常
     */
    public static void validateConfig(@NonNull AgentConfig config) {
        String url = config.getWebsocket().getUrl();
        if (StringUtils.isBlank(url)) {
            throw AgentException.invalidConfig("url", "WebSocket URL 不能为空");
        }

        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            throw AgentException.invalidConfig("url", "URL 必须以 ws:// 或 wss:// 开头");
        }

        if (StringUtils.isBlank(config.getCredentials().getRobotKey())) {
            throw AgentException.invalidConfig("robotKey", "机器人 Key 不能为空");
        }

        if (StringUtils.isBlank(config.getCredentials().getRobotToken())) {
            throw AgentException.invalidConfig("robotToken", "机器人 Token 不能为空");
        }

        if (StringUtils.isBlank(config.getCredentials().getUsername())) {
            throw AgentException.invalidConfig("username", "用户名不能为空");
        }
    }

    // ============================================================================
    // ID 生成
    // ============================================================================

    /**
     * 生成唯一会话标识
     *
     * @return 会话标识字符串
     */
    @NonNull
    public static String generateSegmentCode() {
        long timestamp = System.currentTimeMillis();
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "seg_" + timestamp + "_" + random;
    }

    /**
     * 生成唯一消息 ID
     *
     * @param prefix ID 前缀
     * @return 消息 ID 字符串
     */
    @NonNull
    public static String generateMessageId(@Nullable String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            prefix = "msg";
        }
        long timestamp = System.currentTimeMillis();
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return prefix + "_" + timestamp + "_" + random;
    }

    // ============================================================================
    // 选项合并
    // ============================================================================

    /**
     * 合并选项（通用版本）
     *
     * @param defaultOptions 默认选项
     * @param currentOptions 当前选项
     * @param <T>            选项类型
     * @return 合并后的选项
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T mergeOptions(@Nullable T defaultOptions, @Nullable T currentOptions) {
        if (defaultOptions == null && currentOptions == null) {
            return null;
        }
        if (defaultOptions == null) {
            return currentOptions;
        }
        if (currentOptions == null) {
            return defaultOptions;
        }
        T result = BeanUtil.copyProperties(defaultOptions, (Class<T>) defaultOptions.getClass());
        BeanUtil.copyProperties(currentOptions, result);
        return result;
    }

    // ============================================================================
    // 格式化
    // ============================================================================

    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    /**
     * 替换模板字符串中的变量
     * 将模板字符串中的 {{variable}} 格式的变量替换为对应的值
     *
     * @param template 模板字符串
     * @param params   变量参数对象
     * @return 替换后的字符串
     */
    @Nullable
    public static String replaceTemplateVariables(@Nullable String template, @Nullable Map<String, String> params) {
        if (template == null || params == null || params.isEmpty()) {
            return template;
        }

        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = params.get(key);
            if (replacement != null) {
                matcher.appendReplacement(result, replacement);
            } else {
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 格式化会话 ID（用于日志显示）
     *
     * @param sessionId 会话 ID
     * @return 格式化后的会话 ID（空字符串显示为 'default'）
     */
    @NonNull
    public static String formatSessionId(@Nullable String sessionId) {
        return (sessionId == null || sessionId.isEmpty()) ? CybotStarConstants.DEFAULT_SESSION_DISPLAY_NAME : sessionId;
    }

    // ============================================================================
    // 时间处理
    // ============================================================================

    /**
     * 睡眠函数
     *
     * @param ms 等待时间（毫秒）
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> sleep(long ms) {
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Sleep interrupted", e);
            }
        });
    }

    /**
     * 请求间隔控制
     * 确保请求之间有足够的间隔
     *
     * @param lastRequestEndTime 上次请求结束时间
     * @param minInterval        最小间隔时间（毫秒）
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> requestIntervalControl(long lastRequestEndTime, long minInterval) {
        if (lastRequestEndTime > 0) {
            long timeSinceLastRequest = System.currentTimeMillis() - lastRequestEndTime;
            if (timeSinceLastRequest < minInterval) {
                long waitTime = minInterval - timeSinceLastRequest;
                return sleep(waitTime);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 创建延迟 Duration
     *
     * @param ms 毫秒数
     * @return Duration 对象
     */
    public static Duration duration(long ms) {
        return Duration.ofMillis(ms);
    }
}
