package com.brgroup.cybotstar.examples.reactive;

import com.alibaba.fastjson2.JSON;
import com.brgroup.cybotstar.annotation.CybotStarReactiveFlow;
import com.brgroup.cybotstar.reactive.ReactiveFlowClient;
import com.brgroup.cybotstar.flow.model.vo.*;
import com.brgroup.cybotstar.tool.ColorPrinter;
import com.brgroup.cybotstar.tool.ExampleContext;
import com.brgroup.cybotstar.tool.FlowIOUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;

/**
 * Reactive IR Flow 对话流示例
 * 事件订阅保持回调风格，控制方法使用 Mono
 *
 * @author zhiyuan.xi
 */
@Slf4j
@SpringBootApplication
public class ReactiveIrFlowExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(ReactiveIrFlowExample.class, args)) {
            ctx.getBean(ReactiveIrFlowExampleRunner.class).execute();
        }
    }

    @Component
    @Slf4j
    static class ReactiveIrFlowExampleRunner {

        @Autowired
        @CybotStarReactiveFlow("ir-flow")
        private ReactiveFlowClient flow;

        public void execute() {
            ColorPrinter.separator('=', 60);
            ColorPrinter.title("🚀 Reactive IR Flow Runtime 引擎演示");
            ColorPrinter.separator('=', 60);

            FlowIOUtils.StreamConsumer streamConsumer = FlowIOUtils.createStreamConsumer("🤖 Bot: ");
            FlowIOUtils.StreamConsumer outputConsumer = new FlowIOUtils.StreamConsumer("");

            // 注册原始请求回调（用于调试，查看发送的 WebSocket 请求）
//            flow.onRawRequest(payload -> {
//                System.out.print("\n[Raw Request JSON]: ");
//                System.out.print(JSON.toJSONString(payload));
//            });
//
//            // 注册原始响应回调（用于调试，查看接收的 WebSocket 响应）
//            flow.onRawResponse(response -> {
//                System.out.print("\n[Raw Response JSON]: ");
//                System.out.print(JSON.toJSONString(response));
//            });

            flow.onStart((FlowStartVO vo) -> {
                System.out.println("📋 [START] FlowStartVO: " + JSON.toJSONString(vo));
            });

            flow.onEnd((FlowEndVO vo) -> {
                ColorPrinter.printState(flow.getState());
                outputConsumer.chunk("\n\n✅ Flow 已完成\n");
                System.out.println("📋 [END] FlowEndVO: " + JSON.toJSONString(vo));
            });

            flow.onNodeEnter((FlowNodeEnterVO vo) -> {
                System.out.println("📋 [NODE_ENTER] FlowNodeEnterVO: " + JSON.toJSONString(vo));
            });

            flow.onMessage((String msg, boolean isFinished) -> {
                streamConsumer.chunk(msg != null ? msg : "");
                if (isFinished) streamConsumer.complete();
            });

            flow.onWaiting((FlowWaitingVO vo) -> {
                System.out.println("📋 [WAITING] FlowWaitingVO: " + JSON.toJSONString(vo));
                CompletableFuture.runAsync(() -> {
                    String input = FlowIOUtils.readInput();
                    if (input != null) {
                        String lowerInput = input.toLowerCase().trim();
                        if ("quit".equals(lowerInput) || "exit".equals(lowerInput)) {
                            flow.abort("用户主动退出");
                        } else if (!input.trim().isEmpty()) {
                            ColorPrinter.userInput(input);
                            try {
                                flow.send(input).block();
                            } catch (Exception e) {
                                ColorPrinter.error("发送消息失败", e);
                            }
                        }
                    }
                });
            });

            flow.onError((FlowErrorVO vo) -> {
                outputConsumer.chunk("❌ Flow 发生错误: " + vo.getErrorMessage());
                outputConsumer.chunk(", 📊 当前状态: " + flow.getState() + "\n");
                System.out.println("📋 [ERROR] FlowErrorVO: " + JSON.toJSONString(vo));
            });

            flow.onDebug((FlowDebugVO vo) -> {
                System.out.println("📋 [DEBUG] FlowDebugVO: " + JSON.toJSONString(vo));
            });

            flow.onJump((FlowJumpVO vo) -> {
                ColorPrinter.jump("跳转事件: " + vo.getJumpType());
                outputConsumer.chunk("🔄 Jump: " + vo.getJumpType() + "\n");
                System.out.println("📋 [JUMP] FlowJumpVO: " + JSON.toJSONString(vo));
            });

            try {
                // 使用 Mono 启动 Flow
                flow.start("").block();
                System.out.println("Flow 启动完成, Session ID: " + flow.getSessionId());
                // 使用 Mono 等待完成
                flow.done().block();
                System.out.println("✨ 演示完成");
            } catch (Exception e) {
                ColorPrinter.error("Flow 执行出错", e);
            } finally {
                flow.close();
                FlowIOUtils.closeSharedReader();
            }
        }
    }
}
