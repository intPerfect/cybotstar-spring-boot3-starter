package com.brgroup.cybotstar.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Flow 客户端配置
 * <p>
 * 在多配置模式下，作为 {@link CybotStarMultiConfig} 的嵌套配置使用，
 * 配置路径为：cybotstar.flows.&lt;name&gt;.*
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowConfig {
    /**
     * 凭证配置
     */
    @NestedConfigurationProperty
    @Builder.Default
    private CredentialProperties credentials = new CredentialProperties();

    /**
     * WebSocket 配置
     */
    @NestedConfigurationProperty
    @Builder.Default
    private WebSocketProperties websocket = new WebSocketProperties();

    /**
     * Flow 运行时配置
     */
    @NestedConfigurationProperty
    @Builder.Default
    private FlowProperties flow = new FlowProperties();

    /**
     * HTTP 配置
     */
    @NestedConfigurationProperty
    @Builder.Default
    private HttpProperties http = new HttpProperties();

    /**
     * 日志配置
     */
    @NestedConfigurationProperty
    @Builder.Default
    private LogProperties log = new LogProperties();

    // ========== Flow 配置 ==========
    /**
     * 初始问题（用户代码指定，不需要配置）
     */
    private String question;
}
