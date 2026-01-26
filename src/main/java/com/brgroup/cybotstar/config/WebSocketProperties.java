package com.brgroup.cybotstar.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 配置属性
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketProperties {
    /**
     * WebSocket URL
     */
    private String url;

    /**
     * 连接超时时间（毫秒），默认 5000
     */
    @Builder.Default
    private Integer timeout = 5000;

    /**
     * 最大重试次数，默认 3
     */
    @Builder.Default
    private Integer maxRetries = 3;

    /**
     * 重试间隔（毫秒），默认 1000
     */
    @Builder.Default
    private Long retryInterval = 1000L;

    /**
     * 是否自动重连，默认 true
     */
    @Builder.Default
    private Boolean autoReconnect = true;

    /**
     * 心跳间隔（毫秒），默认 30000，设为 0 禁用心跳
     */
    @Builder.Default
    private Long heartbeatInterval = 30000L;
}

