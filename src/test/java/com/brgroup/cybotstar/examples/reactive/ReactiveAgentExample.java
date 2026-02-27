package com.brgroup.cybotstar.examples.reactive;

import cn.hutool.core.lang.UUID;
import com.brgroup.cybotstar.annotation.CybotStarReactiveAgent;
import com.brgroup.cybotstar.reactive.ReactiveAgentClient;
import com.brgroup.cybotstar.agent.model.ModelOptions;
import com.brgroup.cybotstar.tool.ExampleContext;
import com.brgroup.cybotstar.tool.StreamRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * ReactiveAgentClient 使用示例
 * 展示 Mono/Flux 响应式 API 的基础用法和流式响应
 *
 * @author zhiyuan.xi
 */
@Slf4j
@SpringBootApplication
public class ReactiveAgentExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(ReactiveAgentExample.class, args)) {
            ReactiveAgentExampleRunner runner = ctx.getBean(ReactiveAgentExampleRunner.class);
            runner.execute();
        }
    }

    @Component
    @Slf4j
    static class ReactiveAgentExampleRunner {

        @Autowired
        @CybotStarReactiveAgent("finance-agent")
        private ReactiveAgentClient client;

        public void execute() {
            try {
                sendExample();
                streamExample();
            } catch (Exception e) {
                log.error("发生错误", e);
            } finally {
                client.close();
            }
        }

        /**
         * 示例1：send() 返回 Mono，使用 block() 阻塞获取结果
         */
        private void sendExample() {
            log.info("\n=== 示例1：Reactive send() ===");
            try {
                log.info("发送消息: 你好");
                String response = client.prompt("你好").send().block();
                log.info("收到回复: {}", response);
            } catch (Exception e) {
                log.error("基础示例发生错误", e);
            }
        }

        /**
         * 示例2：stream() 返回 Flux，使用 doOnNext/doOnComplete 处理流
         */
        private void streamExample() {
            log.info("\n=== 示例2：Reactive stream() ===");
            try {
                StreamRenderer renderer = new StreamRenderer();

                // 注册 Reasoning 回调
                client.onReasoning(reasoning -> {
                    renderer.startReasoning();
                    renderer.appendReasoning(reasoning);
                });

                ModelOptions modelOptions = ModelOptions.builder()
                        .temperature(0.7)
                        .topP(0.95)
                        .maxTokens(2000)
                        .build();

                String sessionId = UUID.fastUUID().toString();
                log.info("开始发送请求，sessionId: {}", sessionId);

                // 使用 Flux 流式处理
                client.prompt("介绍一下你自己")
                        .session(sessionId)
                        .option(modelOptions)
                        .stream()
                        .doOnNext(chunk -> {
                            if (!renderer.isStreaming()) {
                                renderer.finishReasoning();
                                renderer.start();
                            }
                            renderer.append(chunk);
                        })
                        .doOnComplete(() -> renderer.finish())
                        .blockLast();

            } catch (Exception e) {
                log.error("流式示例发生错误", e);
            }
        }
    }
}
