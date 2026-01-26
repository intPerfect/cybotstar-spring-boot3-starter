package com.brgroup.cybotstar.util;

import cn.hutool.core.bean.BeanUtil;
import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.agent.exception.AgentException;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 客户端工具类
 * 提供配置验证、ID生成、选项合并等客户端相关工具函数
 *
 * @author zhiyuan.xi
 */
public class ClientUtils {

    /**
     * 验证配置完整性
     *
     * @param config 客户端配置
     * @throws AgentException 配置无效时抛出异常
     */
    public static void validateConfig(@NonNull AgentConfig config) {
        // websocket 已通过 @Builder.Default 确保不为null，这里只需要验证其内容
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
        // 使用 Hutool 的 BeanUtil 进行合并
        T result = BeanUtil.copyProperties(defaultOptions, (Class<T>) defaultOptions.getClass());
        BeanUtil.copyProperties(currentOptions, result);
        return result;
    }
}
