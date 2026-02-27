package com.brgroup.cybotstar.examples;

import cn.hutool.core.lang.UUID;
import com.brgroup.cybotstar.spring.annotation.CybotStarAgent;
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.agent.model.ModelOptions;
import com.brgroup.cybotstar.tool.ExampleContext;
import com.brgroup.cybotstar.tool.StreamRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * AgentClient 使用示例
 * 展示基础用法和流式响应两种使用方式
 * 使用多配置方式，通过 @CybotStarAgent 注解注入指定的 AgentClient
 * <p>
 * 示例1：基础用法
 * - 直接使用 AgentClient 发送消息并等待完整响应
 * - 使用 .send() 返回 Mono&lt;String&gt; 进行响应式调用
 * <p>
 * 示例2：流式响应
 * - 链式调用和流式处理
 * - 配置构建器：使用 ModelOptions.builder() 设置模型参数
 * - 原始请求/响应回调：使用 onRawRequest() 和 onRawResponse() 监听 WebSocket 原始数据（用于调试）
 * - Reasoning 回调：使用 onReasoning() 监听 AI 的思考过程
 * <p>
 * 响应式风格：
 * client.prompt(...).send()  -> Mono&lt;String&gt;
 * client.prompt(...).stream() -> Flux&lt;String&gt;
 * <p>
 * 使用 Reactor 操作符处理流：
 * - doOnNext() - 处理每个 chunk
 * - doOnComplete() - 流完成时回调
 * - doOnError() - 错误处理
 */
@Slf4j
@SpringBootApplication
public class AgentExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(AgentExample.class, args)) {
            AgentExampleRunner runner = ctx.getBean(AgentExampleRunner.class);
            runner.execute();
        }
    }

    @Component
    @Slf4j
    static class AgentExampleRunner {

        @Autowired
        @CybotStarAgent("finance-agent")
        private AgentClient client;

        public void execute() {
            try {
                // ========== 示例1：send() 阻塞响应 ==========
                sendExample();

                // ========== 示例2：stream() 流式响应 ========
                streamExample();

            } catch (Exception e) {
                log.error("发生错误", e);
            } finally {
                client.close();
            }
        }

        /**
         * 示例1：基础用法
         * 直接使用 AgentClient 发送消息并等待完整响应
         */
        private void sendExample() {
            log.info("\n=== 示例1：基础用法 ===");

            try {
                // 发送消息并等待完整响应（连接会自动建立）
                log.info("发送消息: 你好");
                String response = client.prompt("你好")
                        .send()
                        .block();  // Mono<String> -> String
                log.info("收到回复: {}", response);
            } catch (Exception e) {
                log.error("基础示例发生错误", e);
            }
        }

        /**
         * 示例2：流式响应
         * 展示链式调用和流式处理的使用
         * 使用 Reactor 的 doOnNext/doOnComplete/doOnError 操作符处理流
         */
        private void streamExample() {
            log.info("\n=== 示例2：流式响应 ===");

            // AGENTS: 以下注释代码会随时切换过来，不要移除
            // // 注册原始请求回调（用于调试，查看发送的 WebSocket 请求）
            // client.onRawRequest(payload -> {
            // System.out.print("\n[Raw Request JSON]: ");
            // System.out.print(JSON.toJSONString(payload));
            // });
            //
            // // 注册原始响应回调（用于调试，查看接收的 WebSocket 响应）
            // client.onRawResponse(response -> {
            // System.out.print("\n[Raw Response JSON]: ");
            // System.out.print(JSON.toJSONString(response));
            // });

            try {
                StreamRenderer renderer = new StreamRenderer();

                // 注册 Reasoning 回调（用于监听 AI 的思考过程）
                client.onReasoning(reasoning -> {
                    renderer.startReasoning();
                    renderer.appendReasoning(reasoning);
                });

                // 可以指定模型参数
                ModelOptions modelOptions = ModelOptions.builder()
                        .temperature(0.7)
                        .topP(0.95)
                        .maxTokens(2000)
                        .build();

                String sessionId = UUID.fastUUID().toString();
                log.info("开始发送请求，sessionId: {}", sessionId);

                // 创建流式请求，使用链式调用（连接会自动建立）
                // stream() 返回 Flux<String>，使用 Reactor 操作符处理流
                client
                        .prompt("介绍一下你自己")
                        .session(sessionId)
                        .option(modelOptions)
                        .stream()
                        .doOnNext(chunk -> {
                            // 当开始接收 answer 时，完成 reasoning 输出并开始 answer
                            if (!renderer.isStreaming()) {
                                renderer.finishReasoning();
                                renderer.start();
                            }
                            renderer.append(chunk);
                        })
                        .doOnComplete(() -> {
                            renderer.finish();
                            log.info("流式响应完成");
                        })
                        .doOnError(e -> {
                            renderer.finish();
                            log.error("流式响应发生错误", e);
                        })
                        .blockLast();  // 等待流完成

            } catch (Exception e) {
                log.error("流式示例发生错误", e);
            }
        }
    }
}
