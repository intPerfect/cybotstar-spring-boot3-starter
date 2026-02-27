package com.brgroup.cybotstar.reactive.config;

import com.brgroup.cybotstar.annotation.CybotStarReactiveAgent;
import com.brgroup.cybotstar.annotation.CybotStarReactiveFlow;
import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.config.CybotStarMultiConfig;
import com.brgroup.cybotstar.config.FlowConfig;
import com.brgroup.cybotstar.reactive.ReactiveAgentClient;
import com.brgroup.cybotstar.reactive.ReactiveFlowClient;
import com.brgroup.cybotstar.util.ClientUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CybotStar Reactive 自动配置类
 * 仅在 Reactor 存在时激活，为每个 agent/flow 配置创建对应的 Reactive 客户端
 *
 * @author zhiyuan.xi
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(Flux.class)
@EnableConfigurationProperties({CybotStarMultiConfig.class})
public class CybotStarReactiveAutoConfiguration {

    @Bean
    public static ReactiveMultiConfigBeanFactoryPostProcessor reactiveMultiConfigPostProcessor() {
        return new ReactiveMultiConfigBeanFactoryPostProcessor();
    }

    @Slf4j
    static class ReactiveMultiConfigBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

        private static final String LOG_PREFIX = "CybotStar";

        @Override
        public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
            if (!(beanFactory instanceof BeanDefinitionRegistry)) {
                log.warn("{} [Reactive Bootstrap] - BeanFactory is not a BeanDefinitionRegistry, skipping", LOG_PREFIX);
                return;
            }
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

            try {
                CybotStarMultiConfig multiConfig = Binder
                        .get(beanFactory.getBean(Environment.class))
                        .bind("cybotstar", CybotStarMultiConfig.class)
                        .orElse(null);

                if (multiConfig == null) return;

                int agentCount = multiConfig.getAgents() != null ? multiConfig.getAgents().size() : 0;
                int flowCount = multiConfig.getFlows() != null ? multiConfig.getFlows().size() : 0;
                if (agentCount == 0 && flowCount == 0) return;

                List<String> agentNames = createReactiveAgentClientBeans(multiConfig, beanFactory, registry);
                List<String> flowNames = createReactiveFlowClientBeans(multiConfig, beanFactory, registry);

                if (!agentNames.isEmpty()) {
                    log.info("{} [Reactive Agent] - 创建 ReactiveAgentClient：{}", LOG_PREFIX, String.join(", ", agentNames));
                }
                if (!flowNames.isEmpty()) {
                    log.info("{} [Reactive Flow] - 创建 ReactiveFlowClient：{}", LOG_PREFIX, String.join(", ", flowNames));
                }
            } catch (BeansException e) {
                // 忽略 Environment 获取失败
            } catch (Exception e) {
                log.error("{} [Reactive Bootstrap] - 初始化失败", LOG_PREFIX, e);
            }
        }

        private List<String> createReactiveAgentClientBeans(CybotStarMultiConfig multiConfig,
                ConfigurableListableBeanFactory beanFactory, BeanDefinitionRegistry registry) {
            List<String> createdNames = new ArrayList<>();
            Map<String, AgentConfig> agents = multiConfig.getAgents();
            if (agents == null || agents.isEmpty()) return createdNames;

            boolean hasSingleAgent = agents.size() == 1;
            String defaultAgentName = hasSingleAgent ? agents.keySet().iterator().next() : null;

            for (Map.Entry<String, AgentConfig> entry : agents.entrySet()) {
                String name = entry.getKey();
                AgentConfig config = entry.getValue();
                try {
                    ClientUtils.validateConfig(config);
                    String beanName = name + "ReactiveAgentClient";

                    // 注册 BeanDefinition（携带 Qualifier 元数据，供 @CybotStarReactiveAgent 匹配）
                    GenericBeanDefinition bd = new GenericBeanDefinition();
                    bd.setBeanClass(ReactiveAgentClient.class);
                    bd.setInstanceSupplier(() -> new ReactiveAgentClient(config));
                    bd.addQualifier(new AutowireCandidateQualifier(CybotStarReactiveAgent.class, name));
                    registry.registerBeanDefinition(beanName, bd);

                    if (hasSingleAgent && name.equals(defaultAgentName)) {
                        registry.registerAlias(beanName, "reactiveAgentClient");
                    }
                    createdNames.add(name);
                } catch (Exception e) {
                    log.error("{} [Reactive Agent] - 创建 ReactiveAgentClient 失败 [{}]: {}", LOG_PREFIX, name, e.getMessage(), e);
                }
            }
            return createdNames;
        }

        private List<String> createReactiveFlowClientBeans(CybotStarMultiConfig multiConfig,
                ConfigurableListableBeanFactory beanFactory, BeanDefinitionRegistry registry) {
            List<String> createdNames = new ArrayList<>();
            Map<String, FlowConfig> flows = multiConfig.getFlows();
            if (flows == null || flows.isEmpty()) return createdNames;

            boolean hasSingleFlow = flows.size() == 1;
            String defaultFlowName = hasSingleFlow ? flows.keySet().iterator().next() : null;

            for (Map.Entry<String, FlowConfig> entry : flows.entrySet()) {
                String name = entry.getKey();
                FlowConfig config = entry.getValue();
                try {
                    String beanName = name + "ReactiveFlowClient";

                    // 注册 BeanDefinition（携带 Qualifier 元数据，供 @CybotStarReactiveFlow 匹配）
                    GenericBeanDefinition bd = new GenericBeanDefinition();
                    bd.setBeanClass(ReactiveFlowClient.class);
                    bd.setInstanceSupplier(() -> new ReactiveFlowClient(config));
                    bd.addQualifier(new AutowireCandidateQualifier(CybotStarReactiveFlow.class, name));
                    registry.registerBeanDefinition(beanName, bd);

                    if (hasSingleFlow && name.equals(defaultFlowName)) {
                        registry.registerAlias(beanName, "reactiveFlowClient");
                    }
                    createdNames.add(name);
                } catch (Exception e) {
                    log.error("{} [Reactive Flow] - 创建 ReactiveFlowClient 失败 [{}]: {}", LOG_PREFIX, name, e.getMessage(), e);
                }
            }
            return createdNames;
        }
    }
}