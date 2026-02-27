package com.brgroup.cybotstar.examples.reactive;

import com.brgroup.cybotstar.annotation.CybotStarReactiveAgent;
import com.brgroup.cybotstar.reactive.ReactiveAgentClient;
import com.brgroup.cybotstar.agent.exception.AgentErrorCode;
import com.brgroup.cybotstar.agent.exception.AgentException;
import com.brgroup.cybotstar.tool.ExampleContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Reactive 压力测试示例
 * 使用 Flux + flatMap 控制并发和调度
 *
 * @author zhiyuan.xi
 */
@Slf4j
@SpringBootApplication
public class ReactiveStressTestExample {

    public static void main(String[] args) {
        try (ExampleContext ctx = ExampleContext.run(ReactiveStressTestExample.class, args)) {
            ctx.getBean(ReactiveStressTestRunner.class).execute();
        }
    }

    enum TestStatus { SUCCESS, FAILED, TIMEOUT_AFTER_RETRY }

    @Data
    static class TestResult {
        int questionIndex;
        String question;
        String sessionId;
        boolean success;
        TestStatus status;
        @Nullable String error;
        long startTime;
        @Nullable Long endTime;
        int responseLength;
        int chunkCount;
        int retryCount;
        long totalWaitTime;
    }

    @Data
    static class RetryConfig {
        int maxRetries = 2;
        long retryDelay = 1000;
        long timeoutThreshold = 60000;
    }

    enum TrafficPattern {
        UNIFORM, PEAK_START, PEAK_MIDDLE, GRADUAL_INCREASE, GRADUAL_DECREASE, RANDOM
    }

    @Component
    @Slf4j
    static class ReactiveStressTestRunner {

        @Autowired
        @CybotStarReactiveAgent("finance-agent")
        private ReactiveAgentClient client;

        private static final int TOTAL_DURATION_SECONDS = 60;
        private static final TrafficPattern PATTERN = TrafficPattern.UNIFORM;
        private static final double PEAK_RATIO = 0.3;
        private static final int CONCURRENCY = 300; // 并发数
        private final RetryConfig retryConfig = new RetryConfig();
        private final AtomicInteger completedCount = new AtomicInteger(0);
        private final AtomicInteger failedCount = new AtomicInteger(0);
        private int totalQuestions = 0;

        public void execute() {
            try {
                List<String> questions = loadQuestions();
                if (questions.isEmpty()) {
                    log.warn("未找到问题文件或文件为空");
                    return;
                }

                totalQuestions = questions.size();
                log.info("加载了 {} 个问题，流量模式: {}, 持续时间: {}s, 并发数: {}", questions.size(), PATTERN, TOTAL_DURATION_SECONDS, CONCURRENCY);

                List<Long> schedule = generateSchedule(questions.size(), TOTAL_DURATION_SECONDS * 1000L, PATTERN);

                // 使用 Flux + flatMap 实现并发执行
                List<TestResult> results = Flux.range(0, questions.size())
                        .flatMap(i ->
                                Mono.delay(Duration.ofMillis(i == 0 ? 0 : Math.max(0, schedule.get(i) - schedule.get(i - 1))))
                                        .then(Mono.defer(() -> testQuestion(i, questions.get(i)))),
                                CONCURRENCY) // 控制最大并发数
                        .collectList()
                        .block();

                if (results != null) {
                    printStatistics(results);
                }
            } catch (Exception e) {
                log.error("压力测试执行出错", e);
            } finally {
                client.close();
            }
        }

        private Mono<TestResult> testQuestion(int index, String question) {
            TestResult result = new TestResult();
            result.setQuestionIndex(index);
            result.setQuestion(question);
            result.setSessionId("stress-reactive-" + index);
            result.setStartTime(System.currentTimeMillis());

            AtomicInteger chunkCount = new AtomicInteger(0);
            StringBuilder responseBuilder = new StringBuilder();

            return executeWithRetry(result, chunkCount, responseBuilder, 0);
        }

        private Mono<TestResult> executeWithRetry(
                TestResult result, AtomicInteger chunkCount,
                StringBuilder responseBuilder, int retryCount) {

            return client.prompt(result.getQuestion())
                    .session(result.getSessionId())
                    .stream()
                    .doOnNext(chunk -> {
                        chunkCount.incrementAndGet();
                        responseBuilder.append(chunk);
                    })
                    .then(Mono.defer(() -> {
                        result.setSuccess(true);
                        result.setStatus(TestStatus.SUCCESS);
                        result.setResponseLength(responseBuilder.length());
                        result.setChunkCount(chunkCount.get());
                        result.setEndTime(System.currentTimeMillis());
                        result.setRetryCount(retryCount);
                        completedCount.incrementAndGet();

                        log.info("[{}/{}] ✅ 完成 ({}ms, {}chunks)",
                                completedCount.get() + failedCount.get(),
                                totalQuestions, result.getEndTime() - result.getStartTime(), chunkCount.get());
                        return Mono.just(result);
                    }))
                    .onErrorResume(e -> {
                        boolean isTimeout = e instanceof AgentException
                                && ((AgentException) e).getCode() == AgentErrorCode.RESPONSE_TIMEOUT;
                        if (isTimeout && retryCount < retryConfig.getMaxRetries()) {
                            log.warn("[{}] 超时重试 {}/{}", result.getQuestionIndex(), retryCount + 1, retryConfig.getMaxRetries());
                            // 重置状态
                            chunkCount.set(0);
                            responseBuilder.setLength(0);
                            // 延迟后重试
                            return Mono.delay(Duration.ofMillis(retryConfig.getRetryDelay()))
                                    .then(executeWithRetry(result, chunkCount, responseBuilder, retryCount + 1));
                        }
                        result.setSuccess(false);
                        result.setStatus(isTimeout ? TestStatus.TIMEOUT_AFTER_RETRY : TestStatus.FAILED);
                        result.setError(e.getMessage());
                        result.setEndTime(System.currentTimeMillis());
                        result.setRetryCount(retryCount);
                        failedCount.incrementAndGet();
                        log.error("[{}] ❌ 失败: {}", result.getQuestionIndex(), e.getMessage());
                        return Mono.just(result);
                    });
        }

        private List<String> loadQuestions() {
            try {
                // 从 classpath 读取资源文件
                var inputStream = getClass().getClassLoader().getResourceAsStream("questions.txt");
                if (inputStream != null) {
                    try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream))) {
                        List<String> questions = reader.lines()
                                .filter(line -> !line.trim().isEmpty())
                                .collect(Collectors.toList());
                        if (!questions.isEmpty()) {
                            return questions;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("读取 questions.txt 失败: {}", e.getMessage());
            }

            log.warn("未找到 questions.txt 文件，使用默认问题");
            // 默认问题
            List<String> defaults = new ArrayList<>();
            defaults.add("你好");
            defaults.add("介绍一下你自己");
            defaults.add("今天天气怎么样？");
            return defaults;
        }

        private List<Long> generateSchedule(int count, long totalDuration, TrafficPattern pattern) {
            List<Long> schedule = new ArrayList<>();
            Random random = new Random();
            for (int i = 0; i < count; i++) {
                double ratio = (double) i / count;
                long delay;
                switch (pattern) {
                    case PEAK_START:
                        delay = (long) (totalDuration * Math.pow(ratio, 0.5));
                        break;
                    case PEAK_MIDDLE:
                        double mid = Math.abs(ratio - 0.5) * 2;
                        delay = (long) (totalDuration * (1 - Math.pow(1 - mid, 2)) * ratio);
                        break;
                    case GRADUAL_INCREASE:
                        delay = (long) (totalDuration * Math.pow(ratio, 2));
                        break;
                    case GRADUAL_DECREASE:
                        delay = (long) (totalDuration * (1 - Math.pow(1 - ratio, 2)));
                        break;
                    case RANDOM:
                        delay = (long) (totalDuration * random.nextDouble());
                        break;
                    default: // UNIFORM
                        delay = (long) (totalDuration * ratio);
                        break;
                }
                schedule.add(delay);
            }
            Collections.sort(schedule);
            return schedule;
        }

        private void printStatistics(List<TestResult> results) {
            long successCount = results.stream().filter(TestResult::isSuccess).count();
            long failCount = results.size() - successCount;
            double avgTime = results.stream()
                    .filter(r -> r.isSuccess() && r.getEndTime() != null)
                    .mapToLong(r -> r.getEndTime() - r.getStartTime())
                    .average().orElse(0);
            double successRate = (double) successCount / results.size() * 100;

            log.info("\n========== 压力测试统计 ==========");
            log.info("总请求数: {}", results.size());
            log.info("成功: {}, 失败: {}", successCount, failCount);
            log.info("成功率: {}%", String.format("%.1f", successRate));
            log.info("平均响应时间: {}ms", String.format("%.0f", avgTime));
            log.info("==================================");
        }
    }
}
