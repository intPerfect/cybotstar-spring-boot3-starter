package com.brgroup.cybotstar.spring.annotation;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.*;

/**
 * CybotStar Flow 客户端注入注解
 * 用于指定注入哪个配置的 FlowClient
 *
 * <p>使用示例：
 * <pre>
 * {@code
 * @Autowired
 * @CybotStarFlow("ir-flow")
 * private FlowClient irFlowClient;
 * }
 * </pre>
 *
 * @author zhiyuan.xi
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface CybotStarFlow {
    /**
     * Flow 配置名称
     * 对应 yml 配置中 cybotstar.flows.{name} 的 name
     *
     * @return 配置名称
     */
    String value();
}
