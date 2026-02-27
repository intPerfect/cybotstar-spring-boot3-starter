package com.brgroup.cybotstar.core.config;

import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.flow.config.FlowConfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * CybotStar 多配置包装类
 * 支持在同一个配置文件中配置多个 Agent 和 Flow
 *
 * @author zhiyuan.xi
 */
@Data
@ConfigurationProperties(prefix = "cybotstar")
public class CybotStarMultiConfig {

    /**
     * Agent 配置 Map
     * Key: 配置名称（如 "finance-agent"）
     * Value: Agent 配置对象
     */
    private Map<String, AgentConfig> agents = new HashMap<>();

    /**
     * Flow 配置 Map
     * Key: 配置名称（如 "ir-flow"）
     * Value: Flow 配置对象
     */
    private Map<String, FlowConfig> flows = new HashMap<>();
}
