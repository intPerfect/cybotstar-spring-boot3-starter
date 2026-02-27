package com.brgroup.cybotstar.agent.config;

import com.brgroup.cybotstar.core.config.CredentialProperties;
import com.brgroup.cybotstar.core.config.WebSocketProperties;
import com.brgroup.cybotstar.core.config.HttpProperties;
import com.brgroup.cybotstar.core.config.LogProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Agent 客户端配置
 * <p>
 * 在多配置模式下，作为 {@link CybotStarMultiConfig} 的嵌套配置使用，
 * 配置路径为：cybotstar.agents.&lt;name&gt;.*
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig {

    /**
     * 凭证配置
     */
    @NestedConfigurationProperty
    @Builder.Default
    @NonNull
    private CredentialProperties credentials = new CredentialProperties();

    /**
     * WebSocket 配置
     */
    @NestedConfigurationProperty
    @Builder.Default
    @NonNull
    private WebSocketProperties websocket = new WebSocketProperties();

    /**
     * HTTP 配置
     */
    @NestedConfigurationProperty
    @Builder.Default
    @NonNull
    private HttpProperties http = new HttpProperties();

    /**
     * 日志配置
     */
    @NestedConfigurationProperty
    @Builder.Default
    @NonNull
    private LogProperties log = new LogProperties();
}
