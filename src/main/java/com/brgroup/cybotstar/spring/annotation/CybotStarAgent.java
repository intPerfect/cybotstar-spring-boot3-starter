package com.brgroup.cybotstar.spring.annotation;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.*;

/**
 * CybotStar Agent 客户端注入注解
 * 用于指定注入哪个配置的 AgentClient
 *
 * <p>使用示例：
 * <pre>
 * {@code
 * @Autowired
 * @CybotStarAgent("finance-agent")
 * private AgentClient financeAgentClient;
 * }
 * </pre>
 *
 * @author zhiyuan.xi
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface CybotStarAgent {
    /**
     * Agent 配置名称
     * 对应 yml 配置中 cybotstar.agents.{name} 的 name
     *
     * @return 配置名称
     */
    String value();
}
