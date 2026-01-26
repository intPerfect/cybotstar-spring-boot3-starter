package com.brgroup.cybotstar.examples;

import com.brgroup.cybotstar.annotation.CybotStarFlow;
import com.brgroup.cybotstar.flow.FlowClient;
import com.brgroup.cybotstar.flow.model.vo.FlowWaitingVO;
import com.brgroup.cybotstar.flow.model.vo.FlowEndVO;
import com.brgroup.cybotstar.flow.model.vo.FlowErrorVO;
import com.brgroup.cybotstar.tool.ExampleContext;
import com.brgroup.cybotstar.tool.FlowIOUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.CompletableFuture;

/**
 * Test Flow 对话流示例
 * 演示如何使用 FlowClient 进行多轮对话
 * 使用多配置方式，通过 @CybotStarFlow 注解注入指定的 FlowClient
 *
 * @author zhiyuan.xi
 */
@Slf4j
@SpringBootApplication
public class TestFlowExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(TestFlowExample.class, args)) {
            TestFlowExampleRunner runner = ctx.getBean(TestFlowExampleRunner.class);
            runner.execute();
        }
    }

    @Component
    @Slf4j
    static class TestFlowExampleRunner {

        @Autowired
        @CybotStarFlow("test-flow")
        private FlowClient flow;

        public void execute() {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🚀 Test Flow Runtime 引擎演示");
            System.out.println("=".repeat(60));

            log.info("FlowClient 已注入完成");

            // 创建输入输出工具
            FlowIOUtils.StreamConsumer streamConsumer = FlowIOUtils.createStreamConsumer("🤖 Bot: ");
            FlowIOUtils.StreamConsumer outputConsumer = new FlowIOUtils.StreamConsumer("");

            // 流式输出 - 使用简化的 MessageHandler（接收 String msg, boolean isFinished）
            flow.onMessage((String msg, boolean isFinished) -> {
                streamConsumer.chunk(msg != null ? msg : "");
                if (isFinished) {
                    streamConsumer.complete();
                }
            });

            // 等待输入 - 接收 FlowWaitingVO（提取的有意义字段）
            flow.onWaiting((FlowWaitingVO vo) -> {
                // 由于 readInput() 是阻塞操作，这里使用异步处理避免阻塞 WebSocket 消息处理线程
                // 如果您的操作是非阻塞的（如更新 UI、设置标志），则不需要异步包装
                CompletableFuture.runAsync(() -> {
                    String input = FlowIOUtils.readInput();
                    if (input != null) {
                        String lowerInput = input.toLowerCase().trim();
                        if ("quit".equals(lowerInput) || "exit".equals(lowerInput)) {
                            System.out.println("👋 用户退出");
                            flow.abort("用户主动退出");
                        } else if (!input.trim().isEmpty()) {
                            System.out.println("👤 User: " + input);
                            try {
                                flow.send(input).join();
                            } catch (Exception e) {
                                log.error("发送消息失败", e);
                            }
                        }
                    }
                });
            });

            // 订阅结束事件 - 接收 FlowEndVO（提取的有意义字段）
            flow.onEnd((FlowEndVO vo) -> {
                outputConsumer.chunk("\n\n✅ Flow 已完成\n");
            });

            // 订阅错误事件 - 接收 FlowErrorVO（提取的有意义字段）
            flow.onError((FlowErrorVO vo) -> {
                outputConsumer.chunk("❌ 错误: " + vo.getErrorMessage() + "\n");
                outputConsumer.chunk("📊 当前状态: " + flow.getState() + "\n");
            });

            try {
                // 启动 Flow（异步，立即返回）
                String sessionId = flow.start("");
                System.out.println("📋 Session ID: " + sessionId);
                // 等待 Flow 完成
                flow.done().join();
                System.out.println("✨ 演示完成");
            } catch (Exception e) {
                log.error("Flow 执行出错", e);
            } finally {
                // 清理资源
                flow.close();
                FlowIOUtils.closeSharedReader();
            }
        }
    }
}
