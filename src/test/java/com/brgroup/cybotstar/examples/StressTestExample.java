package com.brgroup.cybotstar.examples;

import com.brgroup.cybotstar.spring.annotation.CybotStarAgent;
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.tool.ExampleContext;
import com.brgroup.cybotstar.tool.ColorPrinter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 压力测试示例
 * 展示 AgentClient 的并发能力和稳定性
 * 使用 @CybotStarAgent 注解注入指定的 AgentClient
 * <p>
 * 响应式 API 压力测试：
 * - 使用 Reactor 的并行处理能力
 * - 统计成功/失败次数和响应时间
 */
@Slf4j
@SpringBootApplication
public class StressTestExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(StressTestExample.class, args)) {
            StressTestExampleRunner runner = ctx.getBean(StressTestExampleRunner.class);
            runner.execute();
        }
    }

    @Component
    @Slf4j
    static class StressTestExampleRunner {

        @Autowired
        @CybotStarAgent("finance-agent")
        private AgentClient client;

        // 测试配置
        private static final int CONCURRENT_REQUESTS = 5;
        private static final int TOTAL_REQUESTS = 10;

        public void execute() {
            try {
                ColorPrinter.title("🚀 AgentClient 压力测试");
                ColorPrinter.separator('=', 60);
                ColorPrinter.info("并发数: " + CONCURRENT_REQUESTS);
                ColorPrinter.info("总请求数: " + TOTAL_REQUESTS);

                // 统计
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger failureCount = new AtomicInteger(0);
                List<Long> responseTimes = new ArrayList<>();

                // 使用线程池模拟并发
                ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
                CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);

                long startTime = System.currentTimeMillis();

                for (int i = 0; i < TOTAL_REQUESTS; i++) {
                    final int requestIndex = i;
                    executor.submit(() -> {
                        long requestStart = System.currentTimeMillis();
                        try {
                            // 使用响应式 API 发送请求
                            String response = client
                                    .prompt("测试请求 #" + requestIndex + ": 你好")
                                    .session("stress-test-" + requestIndex)
                                    .send()
                                    .block();

                            long responseTime = System.currentTimeMillis() - requestStart;
                            synchronized (responseTimes) {
                                responseTimes.add(responseTime);
                            }
                            successCount.incrementAndGet();
                            log.info("请求 #{} 成功，耗时: {}ms", requestIndex, responseTime);

                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                            log.error("请求 #{} 失败: {}", requestIndex, e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                // 等待所有请求完成
                latch.await(60, TimeUnit.SECONDS);
                executor.shutdown();

                long totalTime = System.currentTimeMillis() - startTime;

                // 输出统计结果
                ColorPrinter.separator('=', 60);
                ColorPrinter.title("📊 测试结果");
                ColorPrinter.info("总请求数: " + TOTAL_REQUESTS);
                ColorPrinter.info("成功: " + successCount.get());
                ColorPrinter.info("失败: " + failureCount.get());
                ColorPrinter.info("总耗时: " + totalTime + "ms");

                if (!responseTimes.isEmpty()) {
                    long avgTime = responseTimes.stream().mapToLong(Long::longValue).sum() / responseTimes.size();
                    long maxTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
                    long minTime = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
                    ColorPrinter.info("平均响应时间: " + avgTime + "ms");
                    ColorPrinter.info("最大响应时间: " + maxTime + "ms");
                    ColorPrinter.info("最小响应时间: " + minTime + "ms");
                }

                ColorPrinter.separator('=', 60);
                ColorPrinter.success("压力测试完成");

            } catch (Exception e) {
                log.error("压力测试发生错误", e);
            } finally {
                client.close();
            }
        }
    }
}
