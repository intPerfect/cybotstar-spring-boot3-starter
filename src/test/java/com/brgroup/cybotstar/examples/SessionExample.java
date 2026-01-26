package com.brgroup.cybotstar.examples;

import com.brgroup.cybotstar.annotation.CybotStarAgent;
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.agent.AgentStream;
import com.brgroup.cybotstar.agent.model.ModelOptions;
import com.brgroup.cybotstar.agent.model.MessageParam;
import com.brgroup.cybotstar.agent.session.SessionContext;
import com.brgroup.cybotstar.examples.mock.SessionMockData;
import com.brgroup.cybotstar.tool.ExampleContext;
import com.brgroup.cybotstar.tool.ColorPrinter;
import com.brgroup.cybotstar.tool.StreamRenderer;
import com.brgroup.cybotstar.util.Constants;
import com.brgroup.cybotstar.util.TimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 示例3：多轮对话
 * 演示使用 AgentClient 进行多轮对话
 * 使用多配置方式，通过 @CybotStarAgent 注解注入指定的 AgentClient
 */
@Slf4j
@SpringBootApplication
public class SessionExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(SessionExample.class, args)) {
            SessionExampleRunner runner = ctx.getBean(SessionExampleRunner.class);
            runner.execute();
        }
    }

    @Component
    @Slf4j
    static class SessionExampleRunner {

        @Autowired
        @CybotStarAgent("finance-agent")
        private AgentClient client;

        public void execute() {

            try {
                ColorPrinter.title("🚀 多轮对话示例");
                ColorPrinter.separator('=', 60);

                StreamRenderer renderer = new StreamRenderer();

                // ============================================================================
                // 步骤 1：分析表格数据
                // ============================================================================
                ColorPrinter.info("[1] 发送数据分析请求");

                // 使用 Mock 数据构建包含数据的完整问题
                String dataContext = SessionMockData.buildTableDataString();
                String analysisQuestion = "各币种的余额分别是多少？";
                String fullQuestion = dataContext + "\n\n" + analysisQuestion;

                ColorPrinter.question("Question: " + analysisQuestion);
                ColorPrinter.info("附带数据：" + SessionMockData.TABLE_DATA.size() + " 行表格数据");

                // 构建模型参数
                ModelOptions modelOptions = ModelOptions.builder()
                        .temperature(0.7)
                        .build();

                // 设置默认会话ID，后续请求自动使用
                client.session("03-agent-session");

                renderer.start();
                // 创建 stream 对象，自动使用默认会话ID
                AgentStream stream1 = client
                        .prompt(fullQuestion)
                        .option(modelOptions)
                        .onChunk(chunk -> renderer.append(chunk))
                        .stream();
                // 等待流完成
                stream1.done().join();
                renderer.finish();

                if (stream1.getDialogId() != null) {
                    ColorPrinter.info("[Dialog ID]: " + stream1.getDialogId());
                }

                TimeUtils.sleep(Constants.SESSION_EXAMPLE_DELAY).join();

                // ============================================================================
                // 步骤 2：测试对话记忆
                // ============================================================================
                ColorPrinter.separator('-', 60);
                ColorPrinter.info("[2] 测试对话记忆");
                String memoryQuestion = "币种第一的余额是多少？";
                ColorPrinter.question("Question: " + memoryQuestion);

                renderer.start();
                // 创建 stream 对象，自动使用之前设置的 sessionId
                AgentStream stream2 = client
                        .prompt(memoryQuestion)
                        .onChunk(chunk -> renderer.append(chunk))
                        .stream();
                // 等待流完成
                stream2.done().join();
                renderer.finish();

                if (stream2.getDialogId() != null) {
                    ColorPrinter.info("[Dialog ID]: " + stream2.getDialogId());
                }

                // ============================================================================
                // 步骤 3：使用对话历史进行新会话
                // ============================================================================
                ColorPrinter.separator('-', 60);
                ColorPrinter.info("[3] 使用对话历史创建新会话");

                // 获取第一个会话的对话历史
                SessionContext oldContext = client.getSessionContext("03-agent-session");
                List<MessageParam> historyMessages = oldContext.getHistoryMessages();

                ColorPrinter.info("附带 4 条历史会话");

                // 创建新的会话，使用对话历史
                String newSessionId = "03-agent-session-new";
                String historyQuestion = "根据之前的对话，币种列表中一共有多少种币？";
                ColorPrinter.question("Question: " + historyQuestion);
                ColorPrinter.info("使用新会话ID: " + newSessionId);

                // AGENTS: 以下注释代码会随时切换过来，不要移除
//                client.onRawRequest(payload -> {
//                    System.out.print("\n[Raw Request JSON]: ");
//                    System.out.print(JSON.toJSONString(payload));
//                });
//
//                client.onRawResponse(response -> {
//                    System.out.print("\n[Raw Response JSON]: ");
//                    System.out.print(JSON.toJSONString(response));
//                });

                renderer.start();
                // 创建新会话，传入对话历史
                AgentStream stream3 = client
                        .prompt(historyQuestion)
                        .session(newSessionId)
                        .messages(historyMessages)
                        .onChunk(chunk -> renderer.append(chunk))
                        .stream();
                // 等待流完成
                stream3.done().join();
                renderer.finish();

                if (stream3.getDialogId() != null) {
                    ColorPrinter.info("[Dialog ID]: " + stream3.getDialogId());
                }

                ColorPrinter.success("演示完成");
            } catch (Exception e) {
                log.error("发生错误", e);
            } finally {
                client.close();
            }
        }
    }
}
