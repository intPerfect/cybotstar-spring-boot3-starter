package com.brgroup.cybotstar.examples;

import cn.hutool.core.lang.UUID;
import com.brgroup.cybotstar.annotation.CybotStarAgent;
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.agent.AgentStream;
import com.brgroup.cybotstar.agent.model.ModelOptions;
import com.brgroup.cybotstar.tool.ExampleContext;
import com.brgroup.cybotstar.tool.StreamRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * 示例2：链式调用流式响应
 * 展示链式调用和流式处理的使用
 * 使用多配置方式，通过 @CybotStarAgent 注解注入指定的 AgentClient
 * <p>
 * 展示简化API的使用：
 * - 配置构建器：使用 ModelParams.builder() 设置模型参数
 * - 原始请求/响应回调：使用 onRawRequest() 和 onRawResponse() 监听 WebSocket 原始数据（用于调试）
 * - Reasoning 回调：使用 onReasoning() 监听 AI 的思考过程
 * <p>
 * 严格遵循 TypeScript SDK 写法：
 * const stream = await client.prompt(...).onChunk(...).stream();
 * await stream.done();
 */
@Slf4j
@SpringBootApplication
public class StreamExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(StreamExample.class, args)) {
            StreamExampleRunner runner = ctx.getBean(StreamExampleRunner.class);
            runner.execute();
        }
    }

    @Component
    @Slf4j
    static class StreamExampleRunner {

        @Autowired
        @CybotStarAgent("finance-agent")
        private AgentClient client;

        public void execute() {

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

                String sessionId = UUID.fastUUID().toString(); //"02-stream-example";
                log.info("开始发送请求，sessionId: {}", sessionId);

                // 创建 stream 对象，使用链式调用（连接会自动建立）
                AgentStream stream = client
                        .prompt("介绍一下你自己")
                        .session(sessionId)
                        .option(modelOptions)
                        .onChunk(chunk -> {
                            // 当开始接收 answer 时，完成 reasoning 输出并开始 answer
                            if (!renderer.isStreaming()) {
                                renderer.finishReasoning();
                                renderer.start();
                            }
                            renderer.append(chunk);
                        })
                        .stream();

                log.info("等待流完成...");
                stream.done().join();
                renderer.finish();

            } catch (Exception e) {
                log.error("发生错误", e);
            } finally {
                client.close();
            }
        }
    }
}
