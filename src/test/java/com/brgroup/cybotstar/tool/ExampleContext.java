package com.brgroup.cybotstar.tool;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 示例上下文工具类
 * 封装 Spring 上下文启动、配置获取和 Bean 获取逻辑
 * 支持 try-with-resources 自动关闭
 *
 * @author zhiyuan.xi
 */
public class ExampleContext implements AutoCloseable {
    private final ConfigurableApplicationContext context;

    private ExampleContext(ConfigurableApplicationContext context) {
        this.context = context;
    }

    /**
     * 启动 Spring Boot 应用并返回 ExampleContext 实例
     *
     * @param primarySource 主类
     * @param args 命令行参数
     * @return ExampleContext 实例
     */
    public static ExampleContext run(Class<?> primarySource, String... args) {
        ConfigurableApplicationContext context = SpringApplication.run(primarySource, args);
        return new ExampleContext(context);
    }

    /**
     * 获取配置 Bean
     *
     * @param configClass 配置类类型
     * @param <T> 配置类型
     * @return 配置 Bean 实例
     */
    public <T> T getConfig(Class<T> configClass) {
        return context.getBean(configClass);
    }

    /**
     * 获取 Bean
     *
     * @param beanClass Bean 类型
     * @param <T> Bean 类型
     * @return Bean 实例
     */
    public <T> T getBean(Class<T> beanClass) {
        return context.getBean(beanClass);
    }

    /**
     * 关闭 Spring 上下文
     */
    @Override
    public void close() {
        if (context != null) {
            context.close();
        }
    }
}

