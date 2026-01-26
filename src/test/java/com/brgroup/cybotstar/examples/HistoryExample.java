package com.brgroup.cybotstar.examples;

import cn.hutool.core.lang.UUID;
import com.brgroup.cybotstar.annotation.CybotStarAgent;
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.agent.AgentStream;
import com.brgroup.cybotstar.agent.model.MessageParam;

import static com.brgroup.cybotstar.agent.model.MessageParam.*;

import com.brgroup.cybotstar.tool.ColorPrinter;
import com.brgroup.cybotstar.tool.ExampleContext;
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
 * 示例6：使用 messages 设置消息参数
 * <p>
 * 演示使用 AgentClient 的 .messages() 方法设置消息参数：
 * - 使用 .messages() 设置 system 消息和用户问题
 * - 使用 .messages() 包含历史对话
 * - 统一使用静态方法构造 MessageParam
 * - 流式输出示例
 * 使用多配置方式，通过 @CybotStarAgent 注解注入指定的 AgentClient
 */
@Slf4j
@SpringBootApplication
public class HistoryExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(HistoryExample.class, args)) {
            MessagesExampleRunner runner = ctx.getBean(MessagesExampleRunner.class);
            runner.execute();
        }
    }

    @Component
    @Slf4j
    static class MessagesExampleRunner {

        @Autowired
        @CybotStarAgent("finance-agent")
        private AgentClient client;

        public void execute() {
            log.debug("\n=== 示例6：使用 messages 设置消息参数 ===");

            try {
                ColorPrinter.title("🚀 Messages 消息参数示例");
                ColorPrinter.separator('=', 60);

                StreamRenderer renderer = new StreamRenderer();

                // ============================================================================
                // 测试1: 包含历史对话的 .messages()
                // ============================================================================
                ColorPrinter.separator('-', 60);
                ColorPrinter.info("\n[2] 包含历史对话的 .messages()");

                List<MessageParam> messages2 = Arrays.asList(
                        system("你是一个蔬菜店数据分析管家"),
                        user("几个蔬菜店黄瓜的剩余数量是？"),
                        assistant("几个蔬菜店黄瓜的剩余数量如下：\\n 鲜丰蔬菜店\\r 84\\n绿源农贸\\r152\\n便民蔬菜铺\\r67\\n四季鲜果菜\\r203\\n惠民蔬菜超市\\r45。哪个店的剩余数量最多呢？"),
                        user("剩余黄瓜最多的店，有多少黄瓜呢？"),
                        assistant("根据您提供的数据，剩余数量最多的店有203根黄瓜"));
                String question = "鲜丰蔬菜店有多少黄瓜呢？";

                // 切换会话ID
                client.session(UUID.fastUUID().toString());

                renderer.start();
                ColorPrinter.question("Question: " + question);
                ColorPrinter.info("附带历史对话");
                AgentStream stream2 = client.prompt(question)
                        .messages(messages2)
                        .onChunk(chunk -> renderer.append(chunk))
                        .stream();

                // 等待流完成
                stream2.done().join();
                renderer.finish();

                TimeUtils.sleep(Constants.SESSION_EXAMPLE_DELAY).join();

                // ============================================================================
                // 清理资源
                // ============================================================================
                ColorPrinter.info("已断开所有连接");
                ColorPrinter.success("演示完成");

            } catch (Exception e) {
                log.error("发生错误", e);
            } finally {
                client.close();
            }
        }
    }
}
