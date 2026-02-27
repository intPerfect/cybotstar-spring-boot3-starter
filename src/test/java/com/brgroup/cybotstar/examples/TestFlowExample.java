package com.brgroup.cybotstar.examples;

import com.brgroup.cybotstar.annotation.CybotStarFlow;
import com.brgroup.cybotstar.flow.FlowClient;
import com.brgroup.cybotstar.flow.model.vo.FlowMessageVO;
import com.brgroup.cybotstar.flow.model.vo.FlowEndVO;
import com.brgroup.cybotstar.flow.model.vo.FlowErrorVO;
import com.brgroup.cybotstar.tool.ExampleContext;
import com.brgroup.cybotstar.tool.ColorPrinter;
import com.brgroup.cybotstar.tool.StreamRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * FlowClient 使用示例
 * 展示 Flow 的基础用法，包括启动、交互和完成
 * 使用 @CybotStarFlow 注解注入指定的 FlowClient
 * <p>
 * 响应式 API：
 * - flow.start(input) -> Mono&lt;String&gt; (返回 sessionId)
 * - flow.send(input)  -> Mono&lt;Void&gt;
 * - flow.done()       -> Mono&lt;Void&gt; (等待 Flow 完成)
 * <p>
 * 事件订阅（回调风格）：
 * - onMessage(handler) - 接收消息
 * - onEnd(handler)     - Flow 完成
 * - onError(handler)   - 错误处理
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
            try {
                ColorPrinter.title("🚀 Flow 基础示例");
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
                ColorPrinter.info("启动 Flow...");
                String sessionId = flow.start("讲一个笑话")
                        .block();  // Mono<String> -> String

                ColorPrinter.info("Flow 已启动，sessionId: " + sessionId);

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
