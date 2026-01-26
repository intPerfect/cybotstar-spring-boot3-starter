package com.brgroup.cybotstar;

import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.agent.api.AgentApiClient;
import com.brgroup.cybotstar.agent.api.CybotStarHttpClient;
import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.config.CybotStarMultiConfig;
import com.brgroup.cybotstar.config.FlowConfig;
import com.brgroup.cybotstar.flow.FlowClient;
import com.brgroup.cybotstar.util.ClientUtils;
import com.dtflys.forest.springboot.annotation.ForestScan;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CybotStar Agent SDK 自动配置类
 * 支持多配置：在同一个配置文件中配置多个 Agent 和 Flow
 *
 * @author zhiyuan.xi
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({ CybotStarMultiConfig.class })
@ForestScan(basePackages = "com.br.cybotstar.agent.api")
public class CybotStarAutoConfiguration {

    /**
     * 多配置 Bean 工厂后处理器
     * 在配置属性绑定后、Bean 实例化前，为每个配置创建对应的客户端 Bean 定义
     */
    @Bean
    public static MultiConfigBeanFactoryPostProcessor multiConfigBeanFactoryPostProcessor() {
        return new MultiConfigBeanFactoryPostProcessor();
    }

    /**
     * 多配置 Bean 工厂后处理器实现
     * 在配置属性绑定后、Bean 实例化前执行，注册 Bean 定义
     */
    @Slf4j
    static class MultiConfigBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

        private static final String SDK_VERSION = "1.0.0";
        private static final String LOG_PREFIX = "CybotStar";

        @Override
        public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
            long startTime = System.currentTimeMillis();

            try {
                // 绑定配置
                CybotStarMultiConfig multiConfig = Binder
                        .get(beanFactory.getBean(Environment.class))
                        .bind("cybotstar", CybotStarMultiConfig.class)
                        .orElse(null);

                if (multiConfig == null) {
                    log.debug("{} [Bootstrap] - 未找到 cybotstar 配置，跳过自动配置", LOG_PREFIX);
                    return;
                }

                int agentCount = multiConfig.getAgents() != null ? multiConfig.getAgents().size() : 0;
                int flowCount = multiConfig.getFlows() != null ? multiConfig.getFlows().size() : 0;

                if (agentCount == 0 && flowCount == 0) {
                    log.warn("{} [Bootstrap] - 检测到 cybotstar 配置，但 agents 和 flows 均为空。", LOG_PREFIX);
                    log.warn("{} [Bootstrap] - 请在 application.yml 中配置 cybotstar.agents 或 cybotstar.flows", LOG_PREFIX);
                    log.warn("{} [Bootstrap] - 配置示例：", LOG_PREFIX);
                    log.warn("{} [Bootstrap] -   cybotstar:", LOG_PREFIX);
                    log.warn("{} [Bootstrap] -     agents:", LOG_PREFIX);
                    log.warn("{} [Bootstrap] -       your-agent-name:", LOG_PREFIX);
                    log.warn("{} [Bootstrap] -         credentials: ...", LOG_PREFIX);
                    return;
                }

                log.info("{} [Bootstrap] - 多配置加载完成：agents={}，flows={}", LOG_PREFIX, agentCount, flowCount);
                log.info("{} [Bootstrap] - 开始创建客户端（多配置模式）", LOG_PREFIX);

                // 获取 HttpClient
                CybotStarHttpClient httpClient = null;
                try {
                    if (beanFactory.containsBean("cybotStarHttpClient")) {
                        httpClient = beanFactory.getBean(CybotStarHttpClient.class);
                    }
                } catch (BeansException e) {
                    // HttpClient 可能尚未创建，忽略
                }

                // 创建客户端 Bean
                List<String> agentNames = createAgentClientBeans(multiConfig, beanFactory, httpClient);
                List<String> flowNames = createFlowClientBeans(multiConfig, beanFactory);

                // 根据配置动态设置 Flow 日志级别
                applyFlowLogLevels(multiConfig);

                // 输出创建结果
                if (!agentNames.isEmpty()) {
                    log.info("{} [Agent] - 创建 AgentClient：{}", LOG_PREFIX, String.join(", ", agentNames));
                }
                if (!flowNames.isEmpty()) {
                    log.info("{} [Flow] - 创建 FlowClient：{}", LOG_PREFIX, String.join(", ", flowNames));
                }

                // 输出初始化完成信息
                long duration = System.currentTimeMillis() - startTime;
                log.info("{} v{} initialized successfully in {}ms (agents={}, flows={})",
                        LOG_PREFIX, SDK_VERSION, duration, agentCount, flowCount);

            } catch (BeansException e) {
                // 忽略 Environment 获取失败
            } catch (Exception e) {
                log.error("{} [Bootstrap] - 初始化失败", LOG_PREFIX, e);
            }
        }

        /**
         * 为每个 Agent 配置创建 AgentClient Bean
         *
         * @return 成功创建的 Agent 配置名称列表
         */
        private List<String> createAgentClientBeans(CybotStarMultiConfig multiConfig,
                ConfigurableListableBeanFactory beanFactory,
                CybotStarHttpClient httpClient) {
            List<String> createdNames = new ArrayList<>();
            Map<String, AgentConfig> agents = multiConfig.getAgents();
            if (agents == null || agents.isEmpty()) {
                return createdNames;
            }

            boolean hasSingleAgent = agents.size() == 1;
            String defaultAgentName = hasSingleAgent ? agents.keySet().iterator().next() : null;

            for (Map.Entry<String, AgentConfig> entry : agents.entrySet()) {
                String name = entry.getKey();
                AgentConfig config = entry.getValue();

                try {
                    ClientUtils.validateConfig(config);

                    // 创建 AgentClient Bean
                    String beanName = name + "AgentClient";
                    AgentClient agentClient = new AgentClient(config);
                    beanFactory.registerSingleton(beanName, agentClient);
                    beanFactory.registerAlias(beanName, name);

                    // 创建 AgentApiClient Bean
                    if (httpClient != null) {
                        String apiClientBeanName = name + "AgentApiClient";
                        AgentApiClient agentApiClient = new AgentApiClient(config, httpClient);
                        beanFactory.registerSingleton(apiClientBeanName, agentApiClient);
                        beanFactory.registerAlias(apiClientBeanName, name + "ApiClient");
                    }

                    // 单个配置时创建默认 Bean
                    if (hasSingleAgent && name.equals(defaultAgentName)) {
                        beanFactory.registerAlias(beanName, "agentClient");
                        if (httpClient != null) {
                            beanFactory.registerAlias(name + "AgentApiClient", "agentApiClient");
                        }
                    }

                    createdNames.add(name);
                } catch (Exception e) {
                    log.error("{} [Agent] - 创建 AgentClient 失败 [{}]: {}", LOG_PREFIX, name, e.getMessage(), e);
                }
            }

            return createdNames;
        }

        /**
         * 为每个 Flow 配置创建 FlowClient Bean
         *
         * @return 成功创建的 Flow 配置名称列表
         */
        private List<String> createFlowClientBeans(CybotStarMultiConfig multiConfig,
                ConfigurableListableBeanFactory beanFactory) {
            List<String> createdNames = new ArrayList<>();
            Map<String, FlowConfig> flows = multiConfig.getFlows();
            if (flows == null || flows.isEmpty()) {
                return createdNames;
            }

            boolean hasSingleFlow = flows.size() == 1;
            String defaultFlowName = hasSingleFlow ? flows.keySet().iterator().next() : null;

            for (Map.Entry<String, FlowConfig> entry : flows.entrySet()) {
                String name = entry.getKey();
                FlowConfig config = entry.getValue();

                try {
                    // 创建 FlowClient Bean
                    String beanName = name + "FlowClient";
                    FlowClient flowClient = new FlowClient(config);
                    beanFactory.registerSingleton(beanName, flowClient);
                    beanFactory.registerAlias(beanName, name);

                    // 单个配置时创建默认 Bean
                    if (hasSingleFlow && name.equals(defaultFlowName)) {
                        beanFactory.registerAlias(beanName, "flowClient");
                    }

                    createdNames.add(name);
                } catch (Exception e) {
                    log.error("{} [Flow] - 创建 FlowClient 失败 [{}]: {}", LOG_PREFIX, name, e.getMessage(), e);
                }
            }

            return createdNames;
        }

        /**
         * 根据 yml 配置动态设置 Flow 日志级别
         *
         * @param multiConfig 多配置对象
         */
        private void applyFlowLogLevels(CybotStarMultiConfig multiConfig) {
            Map<String, FlowConfig> flows = multiConfig.getFlows();
            if (flows == null || flows.isEmpty()) {
                return;
            }

            LoggerContext loggerContext = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();

            // 收集所有 Flow 配置的日志级别，取最详细的级别（如果有多个 Flow 配置）
            String maxLogLevel = null;
            for (Map.Entry<String, FlowConfig> entry : flows.entrySet()) {
                FlowConfig config = entry.getValue();
                if (config != null && config.getLog() != null) {
                    String logLevel = config.getLog().getLogLevel();
                    if (logLevel != null && !logLevel.isEmpty()) {
                        // 如果还没有设置，或者当前级别更详细，则更新
                        if (maxLogLevel == null || isMoreDetailed(logLevel, maxLogLevel)) {
                            maxLogLevel = logLevel;
                        }
                    }
                }
            }

            // 如果找到了日志级别配置，则应用到 Flow 包
            if (maxLogLevel != null) {
                try {
                    Level level = Level.toLevel(maxLogLevel.toUpperCase(), Level.INFO);
                    ch.qos.logback.classic.Logger flowLogger = loggerContext.getLogger("com.br.cybotstar.flow");
                    flowLogger.setLevel(level);
                    log.debug("{} [Bootstrap] - Flow 日志级别设置为: {}", LOG_PREFIX, level);
                } catch (Exception e) {
                    log.warn("{} [Bootstrap] - 设置 Flow 日志级别失败: {}", LOG_PREFIX, e.getMessage());
                }
            }
        }

        /**
         * 判断日志级别是否更详细（级别更低）
         * debug < info < warn < error
         */
        private boolean isMoreDetailed(String level1, String level2) {
            int priority1 = getLogLevelPriority(level1);
            int priority2 = getLogLevelPriority(level2);
            return priority1 < priority2;
        }

        /**
         * 获取日志级别的优先级（数字越小越详细）
         */
        private int getLogLevelPriority(String level) {
            if (level == null) {
                return 100;
            }
            String lower = level.toLowerCase();
            switch (lower) {
                case "debug":
                    return 0;
                case "info":
                    return 1;
                case "warn":
                    return 2;
                case "error":
                    return 3;
                default:
                    return 1; // 默认为 info
            }
        }
    }
}
