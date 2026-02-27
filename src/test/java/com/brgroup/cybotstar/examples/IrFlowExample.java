package com.brgroup.cybotstar.examples;

import com.brgroup.cybotstar.annotation.CybotStarFlow;
import com.brgroup.cybotstar.flow.FlowClient;
import com.brgroup.cybotstar.flow.model.vo.FlowMessageVO;
import com.brgroup.cybotstar.flow.model.vo.FlowEndVO;
import com.brgroup.cybotstar.flow.model.vo.FlowErrorVO;
import com.brgroup.cybotstar.flow.model.vo.FlowWaitingVO;
import com.brgroup.cybotstar.tool.ExampleContext;
import com.brgroup.cybotstar.tool.ColorPrinter;
import com.brgroup.cybotstar.tool.StreamRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * IR Flow 示例
 * 展示交互式 Flow 的用法，包括多轮对话和用户输入
 * 使用 @CybotStarFlow 注解注入指定的 FlowClient
 * <p>
 * 响应式 API：
 * - flow.start(input) -> Mono&lt;String&gt; (返回 sessionId)
 * - flow.send(input)  -> Mono&lt;Void&gt;
 * - flow.done()       -> Mono&lt;Void&gt; (等待 Flow 完成)
 * <p>
 * 事件订阅（回调风格）：
 * - onMessage(handler) - 接收消息
 * - onWaiting(handler) - 等待用户输入
 * - onEnd(handler)     - Flow 完成
 * - onError(handler)   - 错误处理
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
            try {
                ColorPrinter.title("🚀 IR Flow 交互式示例");
                ColorPrinter.separator('=', 60);

                StreamRenderer renderer = new StreamRenderer();

                // 注册事件处理器
                flow.onMessage((FlowMessageVO vo) -> {
                    if (!vo.isFinished()) {
                        if (!renderer.isStreaming()) {
                            renderer.start();
                        }
                        renderer.append(vo.getDisplayText());
                    }
                });

                flow.onWaiting((FlowWaitingVO vo) -> {
                    renderer.finish();
                    ColorPrinter.info("等待用户输入...");
                });

                flow.onEnd((FlowEndVO vo) -> {
                    renderer.finish();
                    ColorPrinter.success("Flow 执行完成");
                    if (vo.getFinalText() != null && !vo.getFinalText().isEmpty()) {
                        ColorPrinter.info("最终输出: " + vo.getFinalText());
                    }
                });

                flow.onError((FlowErrorVO vo) -> {
                    renderer.finish();
                    ColorPrinter.error("Flow 错误: " + vo.getErrorMessage());
                });

                // 启动 Flow
                ColorPrinter.info("启动 IR Flow...");
                String sessionId = flow.start("你好")
                        .block();  // Mono<String> -> String

                ColorPrinter.info("Flow 已启动，sessionId: " + sessionId);

                // 等待进入等待状态后发送用户输入
                // 在实际应用中，这里会等待用户从控制台输入
                Thread.sleep(2000);

                // 发送用户输入
                ColorPrinter.info("发送用户输入: 我想查询余额");
                flow.send("我想查询余额").block();

                // 等待 Flow 完成
                flow.done().block();  // Mono<Void> -> 阻塞等待完成

                ColorPrinter.separator('=', 60);
                ColorPrinter.success("示例执行完成");

            } catch (Exception e) {
                log.error("发生错误", e);
            } finally {
                flow.close();
            }
        }
    }
}
