package com.brgroup.cybotstar.examples.reactive;

import com.brgroup.cybotstar.agent.model.MessageParam;
import com.brgroup.cybotstar.annotation.CybotStarReactiveAgent;
import com.brgroup.cybotstar.reactive.ReactiveAgentClient;
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
 * Reactive 多轮对话与历史消息示例
 * 使用 ReactiveAgentClient 的 Flux stream() API
 *
 * @author zhiyuan.xi
 */
@Slf4j
@SpringBootApplication
public class ReactiveSessionExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(ReactiveSessionExample.class, args)) {
            ReactiveSessionExampleRunner runner = ctx.getBean(ReactiveSessionExampleRunner.class);
            runner.execute();
        }
    }

    @Component
    @Slf4j
    static class ReactiveSessionExampleRunner {

        @Autowired
        @CybotStarReactiveAgent("finance-agent")
        private ReactiveAgentClient client;

        public void execute() {
            try {
                ColorPrinter.title("🚀 Reactive 多轮对话与历史消息示例");
                ColorPrinter.separator('=', 60);

                StreamRenderer renderer = new StreamRenderer();

                // 步骤 1：分析表格数据
                step1DataAnalysis(renderer);
                TimeUtils.sleep(Constants.SESSION_EXAMPLE_DELAY).join();

                // 步骤 2：测试对话记忆
                step2ConversationMemory(renderer);
                TimeUtils.sleep(Constants.SESSION_EXAMPLE_DELAY).join();

                // 步骤 3：加载对话历史进行新会话
                step3NewSessionWithHistory(renderer);
                TimeUtils.sleep(Constants.SESSION_EXAMPLE_DELAY).join();

                // 步骤 4：自定义历史消息
                step4CustomHistoryMessages(renderer);

                ColorPrinter.success("演示完成");
            } catch (Exception e) {
                log.error("发生错误", e);
            } finally {
                client.close();
            }
        }

        private void step1DataAnalysis(StreamRenderer renderer) {
            ColorPrinter.info("[步骤1] 发送数据分析请求");
            String dataContext = SessionMockData.buildTableDataString();
            String analysisQuestion = "各币种的余额分别是多少？";
            String fullQuestion = dataContext + "\n\n" + analysisQuestion;
            ColorPrinter.question("Question: " + analysisQuestion);

            ModelOptions modelOptions = ModelOptions.builder().temperature(0.7).build();
            client.session("03-reactive-session");

            renderer.start();
            client.prompt(fullQuestion)
                    .option(modelOptions)
                    .stream()
                    .doOnNext(chunk -> renderer.append(chunk))
                    .doOnComplete(() -> renderer.finish())
                    .blockLast();
        }

        private void step2ConversationMemory(StreamRenderer renderer) {
            ColorPrinter.separator('-', 60);
            ColorPrinter.info("[步骤2] 测试对话记忆");
            String memoryQuestion = "币种第一的余额是多少？";
            ColorPrinter.question("Question: " + memoryQuestion);

            renderer.start();
            client.prompt(memoryQuestion)
                    .stream()
                    .doOnNext(chunk -> renderer.append(chunk))
                    .doOnComplete(() -> renderer.finish())
                    .blockLast();
        }

        private void step3NewSessionWithHistory(StreamRenderer renderer) {
            ColorPrinter.separator('-', 60);
            ColorPrinter.info("[步骤3] 加载对话历史创建新会话");

            SessionContext oldContext = client.getSessionContext("03-reactive-session");
            List<MessageParam> historyMessages = oldContext.getHistoryMessages();
            ColorPrinter.info("附带 4 条历史会话");

            String newSessionId = "03-reactive-session-new";
            String historyQuestion = "根据之前的对话，币种列表中一共有多少种币？";
            ColorPrinter.question("Question: " + historyQuestion);

            renderer.start();
            client.prompt(historyQuestion)
                    .session(newSessionId)
                    .messages(historyMessages)
                    .stream()
                    .doOnNext(chunk -> renderer.append(chunk))
                    .doOnComplete(() -> renderer.finish())
                    .blockLast();
        }

        private void step4CustomHistoryMessages(StreamRenderer renderer) {
            ColorPrinter.separator('-', 60);
            ColorPrinter.info("[步骤4] 使用自定义历史消息");

            List<MessageParam> customHistory = Arrays.asList(
                    system("你是一个蔬菜店数据分析管家"),
                    user("几个蔬菜店黄瓜的剩余数量是？"),
                    assistant("几个蔬菜店黄瓜的剩余数量如下：\\n 鲜丰蔬菜店\\r 84\\n绿源农贸\\r152\\n便民蔬菜铺\\r67\\n四季鲜果菜\\r203\\n惠民蔬菜超市\\r45。哪个店的剩余数量最多呢？"),
                    user("剩余黄瓜最多的店，有多少黄瓜呢？"),
                    assistant("根据您提供的数据，剩余数量最多的店有203根黄瓜"));

            String customQuestion = "鲜丰蔬菜店有多少黄瓜呢？";
            ColorPrinter.question("Question: " + customQuestion);

            renderer.start();
            client.prompt(customQuestion)
                    .session("03-reactive-session-custom")
                    .messages(customHistory)
                    .stream()
                    .doOnNext(chunk -> renderer.append(chunk))
                    .doOnComplete(() -> renderer.finish())
                    .blockLast();
        }
    }
}
