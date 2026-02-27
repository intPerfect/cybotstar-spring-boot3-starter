package com.brgroup.cybotstar.tool;

import com.brgroup.cybotstar.config.CredentialProperties;
import com.brgroup.cybotstar.config.FlowConfig;
import com.brgroup.cybotstar.config.FlowProperties;
import com.brgroup.cybotstar.config.WebSocketProperties;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * Flow 配置加载工具类
 * 从 YAML 文件加载 FlowConfig
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class FlowConfigLoader {

    /**
     * 从 YAML 文件加载指定 profile 的 FlowConfig
     * 使用 Spring Boot 多配置格式：cybotstar.flows.{profileName}
     *
     * @param profileName profile 名称（如：test-flow, ir-flow）
     * @return FlowConfig 配置对象
     */
    @SuppressWarnings("unchecked")
    public static FlowConfig loadFromYaml(String profileName) {
        try {
            InputStream inputStream = FlowConfigLoader.class.getResourceAsStream("/application.yml");
            if (inputStream == null) {
                log.warn("无法找到 application.yml 文件");
                return null;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);
            inputStream.close();

            if (data == null) {
                log.warn("YAML 文件为空");
                return null;
            }

            // 从 Spring Boot 多配置格式加载：cybotstar.flows.{profileName}
            Map<String, Object> cybotstar = getMapValue(data, "cybotstar");
            if (cybotstar == null) {
                log.warn("YAML 文件中没有找到 cybotstar 配置");
                return null;
            }

            Map<String, Object> flows = getMapValue(cybotstar, "flows");
            if (flows == null) {
                log.warn("YAML 文件中没有找到 flows 配置");
                return null;
            }

            if (!flows.containsKey(profileName)) {
                log.warn("未找到 profile: {}", profileName);
                return null;
            }

            Object flowProfileObj = flows.get(profileName);
            if (!(flowProfileObj instanceof Map)) {
                log.warn("profile {} 格式不正确", profileName);
                return null;
            }

            Map<String, Object> flowConfigMap = (Map<String, Object>) flowProfileObj;
            log.debug("从 Spring Boot 多配置格式加载 Flow 配置: {}", profileName);
            return buildFlowConfig(flowConfigMap);
        } catch (Exception e) {
            log.error("从 YAML 文件加载配置失败", e);
            return null;
        }
    }

    /**
     * 构建 FlowConfig 对象
     */
    @SuppressWarnings("unchecked")
    private static FlowConfig buildFlowConfig(Map<String, Object> flowConfigMap) {
        FlowConfig.FlowConfigBuilder builder = FlowConfig.builder();

        // 加载 credentials
        Map<String, Object> credentialsMap = getMapValue(flowConfigMap, "credentials");
        if (credentialsMap != null) {
            CredentialProperties.CredentialPropertiesBuilder credBuilder = CredentialProperties.builder();
            credBuilder.robotKey(getStringValue(credentialsMap, "robot-key", ""));
            credBuilder.robotToken(getStringValue(credentialsMap, "robot-token", ""));
            credBuilder.username(getStringValue(credentialsMap, "username", ""));
            builder.credentials(credBuilder.build());
        }

        // 加载 websocket
        Map<String, Object> websocketMap = getMapValue(flowConfigMap, "websocket");
        if (websocketMap != null) {
            WebSocketProperties.WebSocketPropertiesBuilder wsBuilder = WebSocketProperties.builder();
            wsBuilder.url(getStringValue(websocketMap, "url", ""));
            wsBuilder.timeout(getIntegerValue(websocketMap, "timeout", 10000));
            wsBuilder.maxRetries(getIntegerValue(websocketMap, "max-retries", 3));
            wsBuilder.retryInterval(getLongValue(websocketMap, "retry-interval", 1000L));
            wsBuilder.autoReconnect(getBooleanValue(websocketMap, "auto-reconnect", true));
            wsBuilder.heartbeatInterval(getLongValue(websocketMap, "heartbeat-interval", 30000L));
            builder.websocket(wsBuilder.build());
        }

        // 加载 flow 运行时配置
        Map<String, Object> flowMap = getMapValue(flowConfigMap, "flow");
        if (flowMap != null) {
            FlowProperties.FlowPropertiesBuilder flowBuilder = FlowProperties.builder();
            flowBuilder.openFlowTrigger(getStringValue(flowMap, "open-flow-trigger", "direct"));
            flowBuilder.openFlowUuid(getStringValue(flowMap, "open-flow-uuid", null));
            flowBuilder.openFlowNodeUuid(getStringValue(flowMap, "open-flow-node-uuid", null));
            flowBuilder.openFlowDebug(getBooleanValue(flowMap, "open-flow-debug", false));
            
            // 加载 openFlowNodeInputs（如果存在）
            Object nodeInputsObj = flowMap.get("open-flow-node-inputs");
            if (nodeInputsObj instanceof Map) {
                flowBuilder.openFlowNodeInputs((Map<String, Object>) nodeInputsObj);
            }
            
            builder.flow(flowBuilder.build());
        }

        // 加载 flow 特定配置（question 保留在 FlowConfig 中）
        builder.question(getStringValue(flowConfigMap, "question", null));

        return builder.build();
    }

    /**
     * 从 Map 中获取字符串值
     */
    private static String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    /**
     * 从 Map 中获取整数值
     */
    private static Integer getIntegerValue(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 从 Map 中获取长整数值
     */
    private static Long getLongValue(Map<String, Object> map, String key, Long defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 从 Map 中获取布尔值
     */
    private static Boolean getBooleanValue(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    /**
     * 从 Map 中获取嵌套的 Map 值
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMapValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
}

