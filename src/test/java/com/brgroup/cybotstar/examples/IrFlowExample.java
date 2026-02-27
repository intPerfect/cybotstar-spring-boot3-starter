package com.brgroup.cybotstar.examples;

import com.alibaba.fastjson2.JSON;
import com.brgroup.cybotstar.annotation.CybotStarFlow;
import com.brgroup.cybotstar.flow.FlowClient;
import com.brgroup.cybotstar.flow.model.vo.FlowStartVO;
import com.brgroup.cybotstar.flow.model.vo.FlowNodeEnterVO;
import com.brgroup.cybotstar.flow.model.vo.FlowEndVO;
import com.brgroup.cybotstar.flow.model.vo.FlowErrorVO;
import com.brgroup.cybotstar.flow.model.vo.FlowDebugVO;
import com.brgroup.cybotstar.flow.model.vo.FlowJumpVO;
import com.brgroup.cybotstar.flow.model.vo.FlowWaitingVO;
import com.brgroup.cybotstar.tool.ColorPrinter;
import com.brgroup.cybotstar.tool.ExampleContext;
import com.brgroup.cybotstar.tool.FlowIOUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.CompletableFuture;

/**
 * IR Flow 对话流示例
 * 演示如何使用 FlowClient 进行多轮对话
 * 使用多配置方式，通过 @CybotStarFlow 注解注入指定的 FlowClient
 *
 * @author zhiyuan.xi
 */
@Slf4j
@SpringBootApplication
public class IrFlowExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(IrFlowExample.class, args)) {
            IrFlowExampleRunner runner = ctx.getBean(IrFlowExampleRunner.class);
            runner.execute();
        }
    }

    @Component
    @Slf4j
    static class IrFlowExampleRunner {

        @Autowired
        @CybotStarFlow("ir-flow")
        private FlowClient flow;

        public void execute() {
            ColorPrinter.separator('=', 60);
            ColorPrinter.title("🚀 IR Flow Runtime 引擎演示");
            ColorPrinter.separator('=', 60);

            // 创建输入输出工具
            FlowIOUtils.StreamConsumer streamConsumer = FlowIOUtils.createStreamConsumer("🤖 Bot: ");
            FlowIOUtils.StreamConsumer outputConsumer = new FlowIOUtils.StreamConsumer("");

            // Flow Start
            flow.onStart((FlowStartVO vo) -> {
                System.out.println("📋 [START] FlowStartVO: " + JSON.toJSONString(vo));
            });

            // Flow End
            flow.onEnd((FlowEndVO vo) -> {
                ColorPrinter.printState(flow);
                outputConsumer.chunk("\n\n✅ Flow 已完成\n");

                System.out.println("📋 [END] FlowEndVO: " + JSON.toJSONString(vo));
            });

            // Node Enter
            flow.onNodeEnter((FlowNodeEnterVO vo) -> {
                System.out.println("📋 [NODE_ENTER] FlowNodeEnterVO: " + JSON.toJSONString(vo));
            });

            // ⭐Message
            flow.onMessage((String msg, boolean isFinished) -> {
                streamConsumer.chunk(msg != null ? msg : "");
                if (isFinished) {
                    streamConsumer.complete();
                }
            });

            // Waiting
            flow.onWaiting((FlowWaitingVO vo) -> {
                System.out.println("📋 [WAITING] FlowWaitingVO: " + JSON.toJSONString(vo));

                // Flow 等待输入...
                // 由于 readInput() 是阻塞操作，这里使用异步处理避免阻塞 WebSocket 消息处理线程
                // 如果您的操作是非阻塞的（如更新 UI、设置标志），则不需要异步包装
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


            // Flow Error
            flow.onError((FlowErrorVO vo) -> {
                outputConsumer.chunk("❌ Flow 发生错误: " + vo.getErrorMessage());
                outputConsumer.chunk(", 📊 当前状态: " + flow.getState() + "\n");

                System.out.println("📋 [ERROR] FlowErrorVO: " + JSON.toJSONString(vo));
            });

            // Flow Debug (open-flow-debug: true)
            flow.onDebug((FlowDebugVO vo) -> {
                System.out.println("📋 [DEBUG] FlowDebugVO: " + JSON.toJSONString(vo));
            });

            // Flow Jump (multi-flow)
            flow.onJump((FlowJumpVO vo) -> {
                ColorPrinter.jump("跳转事件: " + vo.getJumpType());
                outputConsumer.chunk("🔄 Jump: " + vo.getJumpType() + "\n");
                // 打印 FlowJumpVO 对象（JSON 格式，一行）
                System.out.println("📋 [JUMP] FlowJumpVO: " + JSON.toJSONString(vo));
            });

            try {
                // 启动 Flow
                // flow.startFrom("8e9796b8-e976-4646-951a-961f822d3223");
                flow.start("").block();
                System.out.println("Flow 启动完成, Session ID: " + flow.getSessionId());

                // 等待 Flow 完成
                flow.done().block();
                System.out.println("✨ 演示完成");
            } catch (Exception e) {
                ColorPrinter.printState(flow);
                ColorPrinter.error("Flow 执行出错", e);
            } finally {
                // 清理资源
                flow.close();
                FlowIOUtils.closeSharedReader();
            }
        }
    }
}
