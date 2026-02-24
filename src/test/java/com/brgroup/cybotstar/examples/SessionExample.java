package com.brgroup.cybotstar.examples;

import com.brgroup.cybotstar.agent.model.MessageParam;
import com.brgroup.cybotstar.annotation.CybotStarAgent;
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.agent.AgentStream;
import com.brgroup.cybotstar.agent.model.ModelOptions;
import com.brgroup.cybotstar.agent.session.SessionContext;

import static com.brgroup.cybotstar.agent.model.MessageParam.*;
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

import java.util.Arrays;
import java.util.List;

/**
 * 示例3：多轮对话与历史消息
 *
 * 案例1（步骤1-3）：演示会话管理和对话记忆
 * - 使用默认会话ID进行多轮对话
 * - 测试对话上下文自动记忆
 * - 将历史对话迁移到新会话
 *
 * 案例2（步骤4）：演示自定义历史消息
 * - 手动构建历史消息（system/user/assistant）
 * - 在新会话中使用自定义历史
 *
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
                ColorPrinter.title("🚀 多轮对话与历史消息示例");
                ColorPrinter.separator('=', 60);

                StreamRenderer renderer = new StreamRenderer();

                // ============================================================================
                // 案例1：会话管理和对话记忆
                // ============================================================================

                // 步骤 1：分析表格数据
                step1DataAnalysis(renderer);
                TimeUtils.sleep(Constants.SESSION_EXAMPLE_DELAY).join();

                // 步骤 2：测试对话记忆
                step2ConversationMemory(renderer);
                TimeUtils.sleep(Constants.SESSION_EXAMPLE_DELAY).join();

                // 步骤 3：加载对话历史进行新会话
                step3NewSessionWithHistory(renderer);
                TimeUtils.sleep(Constants.SESSION_EXAMPLE_DELAY).join();

                // ============================================================================
                // 案例2：自定义历史消息
                // ============================================================================
                step4CustomHistoryMessages(renderer);

                ColorPrinter.success("演示完成");
            } catch (Exception e) {
                log.error("发生错误", e);
            } finally {
                client.close();
            }
        }

        /**
         * 案例1 - 步骤 1：分析表格数据
         * 演示使用默认会话ID进行数据分析
         */
        private void step1DataAnalysis(StreamRenderer renderer) {
            ColorPrinter.info("[案例1 - 步骤1] 发送数据分析请求");

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
            AgentStream stream = client
                    .prompt(fullQuestion)
                    .option(modelOptions)
                    .onChunk(chunk -> renderer.append(chunk))
                    .stream();
            // 等待流完成
            stream.done().join();
            renderer.finish();

            if (stream.getDialogId() != null) {
                ColorPrinter.info("[Dialog ID]: " + stream.getDialogId());
            }
        }

        /**
         * 案例1 - 步骤 2：测试对话记忆
         * 演示自动使用之前设置的会话ID，测试对话上下文记忆
         */
        private void step2ConversationMemory(StreamRenderer renderer) {
            ColorPrinter.separator('-', 60);
            ColorPrinter.info("[案例1 - 步骤2] 测试对话记忆");
            String memoryQuestion = "币种第一的余额是多少？";
            ColorPrinter.question("Question: " + memoryQuestion);

            renderer.start();
            // 创建 stream 对象，自动使用之前设置的 sessionId
            AgentStream stream = client
                    .prompt(memoryQuestion)
                    .onChunk(chunk -> renderer.append(chunk))
                    .stream();
            // 等待流完成
            stream.done().join();
            renderer.finish();

            if (stream.getDialogId() != null) {
                ColorPrinter.info("[Dialog ID]: " + stream.getDialogId());
            }
        }

        /**
         * 案例1 - 步骤 3：加载对话历史进行新会话
         * 演示获取旧会话的历史消息，并在新会话中使用
         */
        private void step3NewSessionWithHistory(StreamRenderer renderer) {
            ColorPrinter.separator('-', 60);
            ColorPrinter.info("[案例1 - 步骤3] 加载对话历史创建新会话");

            // 获取第一个会话的对话历史
            SessionContext oldContext = client.getSessionContext("03-agent-session");
            List<MessageParam> historyMessages = oldContext.getHistoryMessages();

            ColorPrinter.info("附带 4 条历史会话");

            // 创建新的会话，加载对话历史
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
            AgentStream stream = client
                    .prompt(historyQuestion)
                    .session(newSessionId)
                    .messages(historyMessages)
                    .onChunk(chunk -> renderer.append(chunk))
                    .stream();
            // 等待流完成
            stream.done().join();
            renderer.finish();

            if (stream.getDialogId() != null) {
                ColorPrinter.info("[Dialog ID]: " + stream.getDialogId());
            }
        }

        /**
         * 案例2 - 步骤 4：使用自定义历史消息
         * 演示手动构建历史消息（system/user/assistant），在新会话中使用
         */
        private void step4CustomHistoryMessages(StreamRenderer renderer) {
            ColorPrinter.separator('-', 60);
            ColorPrinter.info("[案例2] 使用自定义历史消息");

            // 构建自定义历史消息（蔬菜店示例）
            List<MessageParam> customHistory = Arrays.asList(
                    system("你是一个蔬菜店数据分析管家"),
                    user("几个蔬菜店黄瓜的剩余数量是？"),
                    assistant("几个蔬菜店黄瓜的剩余数量如下：\\n 鲜丰蔬菜店\\r 84\\n绿源农贸\\r152\\n便民蔬菜铺\\r67\\n四季鲜果菜\\r203\\n惠民蔬菜超市\\r45。哪个店的剩余数量最多呢？"),
                    user("剩余黄瓜最多的店，有多少黄瓜呢？"),
                    assistant("根据您提供的数据，剩余数量最多的店有203根黄瓜"));

            String customQuestion = "鲜丰蔬菜店有多少黄瓜呢？";
            ColorPrinter.question("Question: " + customQuestion);
            ColorPrinter.info("附带自定义历史对话（5条消息）");

            // 创建新会话使用自定义历史
            String customSessionId = "03-agent-session-custom";

            renderer.start();
            AgentStream stream = client
                    .prompt(customQuestion)
                    .session(customSessionId)
                    .messages(customHistory)
                    .onChunk(chunk -> renderer.append(chunk))
                    .stream();

            stream.done().join();
            renderer.finish();

            if (stream.getDialogId() != null) {
                ColorPrinter.info("[Dialog ID]: " + stream.getDialogId());
            }
        }
    }
}
