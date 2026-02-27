package com.brgroup.cybotstar.annotation;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.*;

/**
 * CybotStar Reactive Flow 客户端注入注解
 * 用于指定注入哪个配置的 ReactiveFlowClient
 *
 * <p>使用示例：
 * <pre>
 * {@code
 * @Autowired
 * @CybotStarReactiveFlow("ir-flow")
 * private ReactiveFlowClient irFlowClient;
 * }
 * </pre>
 *
 * @author zhiyuan.xi
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface CybotStarReactiveFlow {
    /**
     * Flow 配置名称
     * 对应 yml 配置中 cybotstar.flows.{name} 的 name
     *
     * @return 配置名称
     */
    String value();
}
