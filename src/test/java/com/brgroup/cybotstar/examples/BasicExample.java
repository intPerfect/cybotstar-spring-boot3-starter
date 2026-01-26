package com.brgroup.cybotstar.examples;

import com.brgroup.cybotstar.annotation.CybotStarAgent;
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.tool.ExampleContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * 示例1：基础用法
 * 直接使用 AgentClient 发送消息并等待完整响应
 * 使用多配置方式，通过 @CybotStarAgent 注解注入指定的 AgentClient
 */
@Slf4j
@SpringBootApplication
public class BasicExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(BasicExample.class, args)) {
            BasicExampleRunner runner = ctx.getBean(BasicExampleRunner.class);
            runner.execute();
        }
    }

    @Component
    @Slf4j
    static class BasicExampleRunner {

        @Autowired
        @CybotStarAgent("finance-agent")
        private AgentClient client;

        public void execute() {
            log.info("\n=== 示例1：基础用法 ===");
            log.info("AgentClient 已注入完成");

            try {
                // 发送消息并等待完整响应（连接会自动建立）
                log.info("发送消息: 你好");
                String response = client.prompt("你好")
                        .send();
                log.info("收到回复: {}", response);
            } catch (Exception e) {
                log.error("发生错误", e);
            } finally {
                client.disconnect();
            }
        }
    }
}
