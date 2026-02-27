package com.brgroup.cybotstar.examples.reactive;

import com.brgroup.cybotstar.annotation.CybotStarReactiveFlow;
import com.brgroup.cybotstar.reactive.ReactiveFlowClient;
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
 * Reactive Test Flow 对话流示例
 * 事件订阅保持回调风格，控制方法使用 Mono
 *
 * @author zhiyuan.xi
 */
@Slf4j
@SpringBootApplication
public class ReactiveTestFlowExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(ReactiveTestFlowExample.class, args)) {
            ctx.getBean(ReactiveTestFlowExampleRunner.class).execute();
        }
    }

    @Component
    @Slf4j
    static class ReactiveTestFlowExampleRunner {

        @Autowired
        @CybotStarReactiveFlow("test-flow")
        private ReactiveFlowClient flow;

        public void execute() {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🚀 Reactive Test Flow Runtime 引擎演示");
            System.out.println("=".repeat(60));

            FlowIOUtils.StreamConsumer streamConsumer = FlowIOUtils.createStreamConsumer("🤖 Bot: ");
            FlowIOUtils.StreamConsumer outputConsumer = new FlowIOUtils.StreamConsumer("");

            flow.onMessage((String msg, boolean isFinished) -> {
                streamConsumer.chunk(msg != null ? msg : "");
                if (isFinished) streamConsumer.complete();
            });

            flow.onWaiting((FlowWaitingVO vo) -> {
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
                                flow.send(input).block();
                            } catch (Exception e) {
                                log.error("发送消息失败", e);
                            }
                        }
                    }
                });
            });

            flow.onEnd((FlowEndVO vo) -> {
                outputConsumer.chunk("\n\n✅ Flow 已完成\n");
            });

            flow.onError((FlowErrorVO vo) -> {
                outputConsumer.chunk("❌ 错误: " + vo.getErrorMessage() + "\n");
                outputConsumer.chunk("📊 当前状态: " + flow.getState() + "\n");
            });

            try {
                // 使用 Mono 启动 Flow
                String sessionId = flow.start("").block();
                System.out.println("📋 Session ID: " + sessionId);
                // 使用 Mono 等待完成
                flow.done().block();
                System.out.println("✨ 演示完成");
            } catch (Exception e) {
                log.error("Flow 执行出错", e);
            } finally {
                flow.close();
                FlowIOUtils.closeSharedReader();
            }
        }
    }
}
