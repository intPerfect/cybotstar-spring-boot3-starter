package com.brgroup.cybotstar.spring.autoconfigure;

import com.brgroup.cybotstar.spring.annotation.CybotStarAgent;
import com.brgroup.cybotstar.spring.annotation.CybotStarFlow;
import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.core.config.CybotStarMultiConfig;
import com.brgroup.cybotstar.flow.config.FlowConfig;
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.flow.FlowClient;
import com.brgroup.cybotstar.core.util.CybotStarUtils;
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
 * CybotStar 自动配置类
 * 仅在 Reactor 存在时激活，为每个 agent/flow 配置创建对应的客户端
 *
 * @author zhiyuan.xi
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(Flux.class)
@EnableConfigurationProperties({CybotStarMultiConfig.class})
public class CybotStarAutoConfiguration {

    @Bean
    public static MultiConfigBeanFactoryPostProcessor multiConfigPostProcessor() {
        return new MultiConfigBeanFactoryPostProcessor();
    }

    @Slf4j
    static class MultiConfigBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

        private static final String LOG_PREFIX = "CybotStar";

        @Override
        public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
            if (!(beanFactory instanceof BeanDefinitionRegistry)) {
                log.warn("{} [Bootstrap] - BeanFactory is not a BeanDefinitionRegistry, skipping", LOG_PREFIX);
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

                List<String> agentNames = createAgentClientBeans(multiConfig, beanFactory, registry);
                List<String> flowNames = createFlowClientBeans(multiConfig, beanFactory, registry);

                if (!agentNames.isEmpty()) {
                    log.info("{} [Agent] - 创建 AgentClient：{}", LOG_PREFIX, String.join(", ", agentNames));
                }
                if (!flowNames.isEmpty()) {
                    log.info("{} [Flow] - 创建 FlowClient：{}", LOG_PREFIX, String.join(", ", flowNames));
                }
            } catch (BeansException e) {
                // 忽略 Environment 获取失败
            } catch (Exception e) {
                log.error("{} [Bootstrap] - 初始化失败", LOG_PREFIX, e);
            }
        }

        private List<String> createAgentClientBeans(CybotStarMultiConfig multiConfig,
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
                    CybotStarUtils.validateConfig(config);
                    String beanName = name + "AgentClient";

                    // 注册 BeanDefinition（携带 Qualifier 元数据，供 @CybotStarAgent 匹配）
                    GenericBeanDefinition bd = new GenericBeanDefinition();
                    bd.setBeanClass(AgentClient.class);
                    bd.setInstanceSupplier(() -> new AgentClient(config));
                    bd.addQualifier(new AutowireCandidateQualifier(CybotStarAgent.class, name));
                    registry.registerBeanDefinition(beanName, bd);

                    if (hasSingleAgent && name.equals(defaultAgentName)) {
                        registry.registerAlias(beanName, "agentClient");
                    }
                    createdNames.add(name);
                } catch (Exception e) {
                    log.error("{} [Agent] - 创建 AgentClient 失败 [{}]: {}", LOG_PREFIX, name, e.getMessage(), e);
                }
            }
            return createdNames;
        }

        private List<String> createFlowClientBeans(CybotStarMultiConfig multiConfig,
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
                    String beanName = name + "FlowClient";

                    // 注册 BeanDefinition（携带 Qualifier 元数据，供 @CybotStarFlow 匹配）
                    GenericBeanDefinition bd = new GenericBeanDefinition();
                    bd.setBeanClass(FlowClient.class);
                    bd.setInstanceSupplier(() -> new FlowClient(config));
                    bd.addQualifier(new AutowireCandidateQualifier(CybotStarFlow.class, name));
                    registry.registerBeanDefinition(beanName, bd);

                    if (hasSingleFlow && name.equals(defaultFlowName)) {
                        registry.registerAlias(beanName, "flowClient");
                    }
                    createdNames.add(name);
                } catch (Exception e) {
                    log.error("{} [Flow] - 创建 FlowClient 失败 [{}]: {}", LOG_PREFIX, name, e.getMessage(), e);
                }
            }
            return createdNames;
        }
    }
}
