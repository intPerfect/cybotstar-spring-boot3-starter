package com.brgroup.cybotstar.examples;

import com.brgroup.cybotstar.annotation.CybotStarAgent;
import com.brgroup.cybotstar.agent.AgentClient;
import com.brgroup.cybotstar.agent.AgentStream;
import com.brgroup.cybotstar.agent.exception.AgentErrorCode;
import com.brgroup.cybotstar.agent.exception.AgentException;
import com.brgroup.cybotstar.tool.ExampleContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 示例7：压力测试
 * 读取 questions.txt 中的所有问题，按照自然流量波动分布发送给 agent 平台
 * 同时接收流式消息，测试系统承载能力
 * 
 * 特性：
 * - 支持多种流量模式（均匀分布、高峰模式、逐渐增减等）
 * - 可配置总持续时间和高峰比例
 * - 带重试机制的错误处理
 * - 详细的统计信息输出
 * 
 * 基于前端 TypeScript 版本的压测代码实现
 * 使用多配置方式，通过 @CybotStarAgent 注解注入指定的 AgentClient
 * 
 * @author zhiyuan.xi
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

    /**
     * 测试状态枚举
     */
    enum TestStatus {
        SUCCESS,
        FAILED,
        TIMEOUT_AFTER_RETRY
    }

    /**
     * 测试结果
     */
    @Data
    static class TestResult {
        int questionIndex;
        String question;
        String sessionId;
        boolean success;
        TestStatus status;
        @Nullable
        String error;
        long startTime;
        @Nullable
        Long endTime;
        int responseLength;
        int chunkCount;
        int retryCount;
        long totalWaitTime;
    }

    /**
     * 重试配置
     */
    @Data
    static class RetryConfig {
        int maxRetries = 2; // 最大重试次数
        long retryDelay = 1000; // 响应超时重试间隔（毫秒）
        long timeoutThreshold = 60000; // 总等待时间阈值（毫秒），超过此值不计入失败
        boolean retryConnectionErrors = true; // 是否重试连接错误
        long connectionRetryDelay = 3000; // 连接错误重试间隔（毫秒）
    }

    /**
     * 流量模式枚举
     */
    enum TrafficPattern {
        UNIFORM, // 均匀分布
        PEAK_START, // 开始高峰，然后逐渐减少
        PEAK_MIDDLE, // 中间高峰（双峰模式）
        GRADUAL_INCREASE, // 逐渐增加
        GRADUAL_DECREASE, // 逐渐减少
        RANDOM // 随机分布
    }

    /**
     * 流量配置
     */
    @Data
    static class TrafficConfig {
        TrafficPattern pattern = TrafficPattern.PEAK_MIDDLE; // 流量模式
        long totalDurationSeconds = 300; // 总持续时间（秒），默认5分钟
        int peakMultiplier = 3; // 高峰时段的倍数（相对于平均速率）
        double peakRatio = 0.2; // 高峰时段占总时长的比例（0.0-1.0）
    }

    @Component
    @Slf4j
    static class StressTestExampleRunner {

        @Autowired
        @CybotStarAgent("finance-agent")
        private AgentClient client;

        /**
         * 加载问题列表
         */
        private List<String> loadQuestions() {
            try {
                // 尝试从 resources 目录加载
                Path questionsPath = Paths.get("src/test/resources/questions.txt");
                if (!Files.exists(questionsPath)) {
                    // 如果不存在，尝试从类路径加载
                    questionsPath = Paths.get(
                            StressTestExample.class.getClassLoader()
                                    .getResource("com/brgroup/cybotstar/examples/mock/questions.txt")
                                    .toURI());
                }

                List<String> questions = Files.readAllLines(questionsPath)
                        .stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .collect(Collectors.toList());

                log.info("✅ 已加载 {} 个问题", questions.size());
                return questions;
            } catch (Exception e) {
                log.error("❌ 加载问题文件失败", e);
                return new ArrayList<>();
            }
        }

        /**
         * 执行单个问题的压力测试（带重试机制）
         */
        private CompletableFuture<TestResult> testQuestion(
                AgentClient client,
                int questionIndex,
                String question,
                String sessionId,
                RetryConfig retryConfig) {

            return CompletableFuture.supplyAsync(() -> {
                TestResult result = new TestResult();
                result.setQuestionIndex(questionIndex);
                result.setQuestion(question);
                result.setSessionId(sessionId);
                result.setSuccess(false);
                result.setStatus(TestStatus.FAILED);
                result.setStartTime(System.currentTimeMillis());
                result.setResponseLength(0);
                result.setChunkCount(0);
                result.setRetryCount(0);
                result.setTotalWaitTime(0);

                Throwable lastError = null;
                long initialStartTime = System.currentTimeMillis();

                // 重试循环
                for (int attempt = 0; attempt <= retryConfig.getMaxRetries(); attempt++) {
                    try {
                        // 获取流式响应
                        AgentStream stream = client
                                .session(sessionId)
                                .prompt(question)
                                .stream();

                        // 接收流式消息（使用 Iterator 接口迭代）
                        while (stream.hasNext()) {
                            String chunk = stream.next();
                            if (chunk != null) {
                                result.setResponseLength(result.getResponseLength() + chunk.length());
                                result.setChunkCount(result.getChunkCount() + 1);
                            }
                        }

                        // 等待流完成
                        stream.done().join();

                        result.setEndTime(System.currentTimeMillis());
                        result.setSuccess(true);
                        result.setStatus(TestStatus.SUCCESS);
                        result.setTotalWaitTime(result.getEndTime() - initialStartTime);
                        return result;

                    } catch (Exception e) {
                        lastError = e;
                        long attemptEndTime = System.currentTimeMillis();
                        result.setTotalWaitTime(attemptEndTime - initialStartTime);

                        // 检查错误类型
                        boolean isTimeoutError = e instanceof AgentException &&
                                ((AgentException) e)
                                        .getCode() == AgentErrorCode.RESPONSE_TIMEOUT;
                        boolean isConnectionError = e instanceof AgentException &&
                                (((AgentException) e)
                                        .getCode() == AgentErrorCode.CONNECTION_FAILED
                                        ||
                                        ((AgentException) e)
                                                .getCode() == AgentErrorCode.CONNECTION_TIMEOUT);

                        // 判断是否应该重试
                        boolean shouldRetry = (isTimeoutError ||
                                (isConnectionError && retryConfig.isRetryConnectionErrors())) &&
                                attempt < retryConfig.getMaxRetries();

                        // 如果不应重试或已达到最大重试次数，直接失败
                        if (!shouldRetry) {
                            result.setEndTime(attemptEndTime);
                            result.setError(e.getMessage() != null ? e.getMessage() : e.toString());

                            // 如果是超时错误且总等待时间超过阈值，标记为 timeout_after_retry
                            if (isTimeoutError && result.getTotalWaitTime() >= retryConfig.getTimeoutThreshold()) {
                                result.setStatus(TestStatus.TIMEOUT_AFTER_RETRY);
                                result.setSuccess(false);
                            } else {
                                result.setStatus(TestStatus.FAILED);
                                result.setSuccess(false);
                            }
                            break;
                        }

                        // 可以重试
                        result.setRetryCount(attempt + 1);

                        // 如果是超时错误且总等待时间已经超过阈值，标记为 timeout_after_retry 并停止重试
                        if (isTimeoutError && result.getTotalWaitTime() >= retryConfig.getTimeoutThreshold()) {
                            result.setEndTime(attemptEndTime);
                            result.setStatus(TestStatus.TIMEOUT_AFTER_RETRY);
                            result.setSuccess(false);
                            result.setError(String.format("等待响应超时 (总等待时间: %dms, 已重试 %d 次)",
                                    result.getTotalWaitTime(), result.getRetryCount()));
                            break;
                        }

                        // 根据错误类型选择重试延迟
                        long retryDelay = isConnectionError ? retryConfig.getConnectionRetryDelay()
                                : retryConfig.getRetryDelay();

                        // 等待后重试
                        try {
                            Thread.sleep(retryDelay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            result.setEndTime(System.currentTimeMillis());
                            result.setError("线程被中断");
                            break;
                        }
                    }
                }

                // 如果所有重试都失败
                if (result.getEndTime() == null) {
                    result.setEndTime(System.currentTimeMillis());
                    result.setTotalWaitTime(result.getEndTime() - initialStartTime);
                }
                if (result.getError() == null && lastError != null) {
                    result.setError(lastError.getMessage() != null ? lastError.getMessage() : lastError.toString());
                }

                return result;
            });
        }

        /**
         * 打印测试结果统计
         */
        private void printStatistics(List<TestResult> results) {
            int total = results.size();
            long success = results.stream()
                    .filter(r -> r.getStatus() == TestStatus.SUCCESS)
                    .count();
            long failed = results.stream()
                    .filter(r -> r.getStatus() == TestStatus.FAILED)
                    .count();
            long timeoutAfterRetry = results.stream()
                    .filter(r -> r.getStatus() == TestStatus.TIMEOUT_AFTER_RETRY)
                    .count();
            double successRate = total > 0 ? (success * 100.0 / total) : 0;

            List<Double> durations = results.stream()
                    .filter(r -> r.getEndTime() != null && r.getStatus() == TestStatus.SUCCESS)
                    .map(r -> (r.getEndTime() - r.getStartTime()) / 1000.0)
                    .collect(Collectors.toList());
            double avgDuration = durations.isEmpty() ? 0
                    : durations.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double minDuration = durations.isEmpty() ? 0
                    : durations.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double maxDuration = durations.isEmpty() ? 0
                    : durations.stream().mapToDouble(Double::doubleValue).max().orElse(0);

            int totalResponseLength = results.stream()
                    .filter(r -> r.getStatus() == TestStatus.SUCCESS)
                    .mapToInt(TestResult::getResponseLength)
                    .sum();
            int avgResponseLength = success > 0 ? (int) (totalResponseLength / success) : 0;

            int totalChunks = results.stream()
                    .filter(r -> r.getStatus() == TestStatus.SUCCESS)
                    .mapToInt(TestResult::getChunkCount)
                    .sum();
            int avgChunks = success > 0 ? (int) (totalChunks / success) : 0;

            long startTime = results.stream()
                    .mapToLong(TestResult::getStartTime)
                    .min()
                    .orElse(0);
            long endTime = results.stream()
                    .filter(r -> r.getEndTime() != null)
                    .mapToLong(TestResult::getEndTime)
                    .max()
                    .orElse(0);
            double totalTime = (endTime - startTime) / 1000.0;

            // 计算平均重试次数
            List<TestResult> retriedResults = results.stream()
                    .filter(r -> r.getRetryCount() > 0)
                    .collect(Collectors.toList());
            double avgRetryCount = retriedResults.isEmpty() ? 0
                    : retriedResults.stream()
                            .mapToInt(TestResult::getRetryCount)
                            .average()
                            .orElse(0);

            // 计算超时但已重试的平均等待时间
            List<Double> timeoutWaitTimes = results.stream()
                    .filter(r -> r.getStatus() == TestStatus.TIMEOUT_AFTER_RETRY)
                    .map(r -> r.getTotalWaitTime() / 1000.0)
                    .collect(Collectors.toList());
            double avgTimeoutWaitTime = timeoutWaitTimes.isEmpty() ? 0
                    : timeoutWaitTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0);

            log.info("\n" + "=".repeat(80));
            log.info("📊 压力测试统计结果");
            log.info("=".repeat(80));
            log.info("总问题数: {}", total);
            log.info("成功: {} ({}%)", success, String.format("%.2f", successRate));
            log.info("失败: {}", failed);
            if (timeoutAfterRetry > 0) {
                log.info("超时但已重试: {} (不计入失败)", timeoutAfterRetry);
            }
            log.info("总耗时: {}秒", String.format("%.2f", totalTime));
            log.info("平均响应时间: {}秒", String.format("%.2f", avgDuration));
            log.info("最短响应时间: {}秒", String.format("%.2f", minDuration));
            log.info("最长响应时间: {}秒", String.format("%.2f", maxDuration));
            log.info("平均响应长度: {} 字符", avgResponseLength);
            log.info("平均chunk数量: {}", avgChunks);
            log.info("总响应长度: {} 字符", totalResponseLength);
            log.info("总chunk数量: {}", totalChunks);
            if (!retriedResults.isEmpty()) {
                log.info("平均重试次数: {}", String.format("%.2f", avgRetryCount));
            }
            if (timeoutAfterRetry > 0) {
                log.info("超时但已重试的平均等待时间: {}秒", String.format("%.2f", avgTimeoutWaitTime));
            }

            if (failed > 0) {
                log.info("\n❌ 失败的问题:");
                results.stream()
                        .filter(r -> r.getStatus() == TestStatus.FAILED)
                        .forEach(r -> {
                            String questionPreview = r.getQuestion().length() > 50
                                    ? r.getQuestion().substring(0, 50) + "..."
                                    : r.getQuestion();
                            log.info("  [{}] {}", r.getQuestionIndex() + 1, questionPreview);
                            log.info("      错误: {}", r.getError());
                            if (r.getRetryCount() > 0) {
                                log.info("      重试次数: {}", r.getRetryCount());
                            }
                        });
            }

            if (timeoutAfterRetry > 0) {
                log.info("\n⏱️  超时但已重试的问题 (不计入失败):");
                results.stream()
                        .filter(r -> r.getStatus() == TestStatus.TIMEOUT_AFTER_RETRY)
                        .forEach(r -> {
                            String questionPreview = r.getQuestion().length() > 50
                                    ? r.getQuestion().substring(0, 50) + "..."
                                    : r.getQuestion();
                            log.info("  [{}] {}", r.getQuestionIndex() + 1, questionPreview);
                            log.info("      总等待时间: {}秒", String.format("%.2f", r.getTotalWaitTime() / 1000.0));
                            log.info("      重试次数: {}", r.getRetryCount());
                            log.info("      错误: {}", r.getError());
                        });
            }

            log.info("=".repeat(80));
        }

        /**
         * 计算请求发送时间表
         * 根据流量模式和时间窗口，计算每个请求的发送时间
         *
         * @param questionCount 问题总数
         * @param config        流量配置
         * @return 每个请求的发送时间偏移（毫秒），相对于开始时间
         */
        private List<Long> calculateSendSchedule(int questionCount, TrafficConfig config) {
            List<Long> schedule = new ArrayList<>();
            long totalDurationMs = config.getTotalDurationSeconds() * 1000L;
            Random random = new Random();

            switch (config.getPattern()) {
                case UNIFORM:
                    // 均匀分布
                    for (int i = 0; i < questionCount; i++) {
                        schedule.add((long) (i * totalDurationMs / questionCount));
                    }
                    break;

                case PEAK_START:
                    // 开始高峰，然后逐渐减少
                    int peakCount = (int) (questionCount * config.getPeakRatio());
                    long peakDuration = (long) (totalDurationMs * config.getPeakRatio());
                    long normalDuration = totalDurationMs - peakDuration;

                    // 高峰时段：前 peakRatio 的时间内发送 peakCount 个请求
                    for (int i = 0; i < peakCount; i++) {
                        schedule.add((long) (i * peakDuration / peakCount));
                    }

                    // 正常时段：剩余时间内均匀分布剩余请求
                    for (int i = peakCount; i < questionCount; i++) {
                        schedule.add(
                                peakDuration + (long) ((i - peakCount) * normalDuration / (questionCount - peakCount)));
                    }
                    break;

                case PEAK_MIDDLE:
                    // 中间高峰（双峰模式）
                    int peak1Count = (int) (questionCount * config.getPeakRatio() * 0.5);
                    int peak2Count = (int) (questionCount * config.getPeakRatio() * 0.5);
                    int normalCount = questionCount - peak1Count - peak2Count;

                    long peak1Start = (long) (totalDurationMs * 0.3);
                    long peak1End = peak1Start + (long) (totalDurationMs * config.getPeakRatio() * 0.5);
                    long peak2Start = (long) (totalDurationMs * 0.6);
                    long peak2End = peak2Start + (long) (totalDurationMs * config.getPeakRatio() * 0.5);

                    // 第一个高峰
                    for (int i = 0; i < peak1Count; i++) {
                        schedule.add(peak1Start + (long) (i * (peak1End - peak1Start) / peak1Count));
                    }

                    // 第二个高峰
                    for (int i = 0; i < peak2Count; i++) {
                        schedule.add(peak2Start + (long) (i * (peak2End - peak2Start) / peak2Count));
                    }

                    // 正常时段：在高峰之间和前后均匀分布
                    int normalSegments = 3; // 高峰前、两个高峰之间、高峰后
                    int normalPerSegment = normalCount / normalSegments;
                    long segment1End = peak1Start;
                    long segment2Start = peak1End;
                    long segment2End = peak2Start;
                    long segment3Start = peak2End;

                    // 第一段：高峰前
                    for (int i = 0; i < normalPerSegment && schedule.size() < questionCount; i++) {
                        schedule.add((long) (i * segment1End / normalPerSegment));
                    }

                    // 第二段：两个高峰之间
                    for (int i = 0; i < normalPerSegment && schedule.size() < questionCount; i++) {
                        schedule.add(segment2Start + (long) (i * (segment2End - segment2Start) / normalPerSegment));
                    }

                    // 第三段：高峰后
                    for (int i = 0; schedule.size() < questionCount; i++) {
                        schedule.add(segment3Start
                                + (long) (i * (totalDurationMs - segment3Start) / (questionCount - schedule.size())));
                    }
                    break;

                case GRADUAL_INCREASE:
                    // 逐渐增加：开始慢，逐渐加快
                    for (int i = 0; i < questionCount; i++) {
                        double ratio = (double) i / questionCount;
                        // 使用平方函数实现逐渐加速
                        schedule.add((long) (totalDurationMs * ratio * ratio));
                    }
                    break;

                case GRADUAL_DECREASE:
                    // 逐渐减少：开始快，逐渐减慢
                    for (int i = 0; i < questionCount; i++) {
                        double ratio = (double) i / questionCount;
                        // 使用平方根函数实现逐渐减速
                        schedule.add((long) (totalDurationMs * Math.sqrt(ratio)));
                    }
                    break;

                case RANDOM:
                    // 随机分布
                    for (int i = 0; i < questionCount; i++) {
                        schedule.add((long) (random.nextDouble() * totalDurationMs));
                    }
                    Collections.sort(schedule);
                    break;

                default:
                    // 默认均匀分布
                    for (int i = 0; i < questionCount; i++) {
                        schedule.add((long) (i * totalDurationMs / questionCount));
                    }
            }

            return schedule;
        }

        /**
         * 打印实时进度
         */
        private void printProgress(int current, int total, List<TestResult> results) {
            long success = results.stream()
                    .filter(r -> r.getStatus() == TestStatus.SUCCESS)
                    .count();
            long failed = results.stream()
                    .filter(r -> r.getStatus() == TestStatus.FAILED)
                    .count();
            long timeoutAfterRetry = results.stream()
                    .filter(r -> r.getStatus() == TestStatus.TIMEOUT_AFTER_RETRY)
                    .count();
            double progress = (current * 100.0 / total);

            String progressText = String.format("\r进度: %d/%d (%.1f%%) | 成功: %d | 失败: %d",
                    current, total, progress, success, failed);
            if (timeoutAfterRetry > 0) {
                progressText += String.format(" | 超时(已重试): %d", timeoutAfterRetry);
            }
            System.out.print(progressText);
            System.out.flush(); // 立即刷新输出
        }

        public void execute() {
            log.info("\n=== 示例7：压力测试 ===");
            log.info("读取 questions.txt 中的所有问题，并发发送给 agent 平台\n");

            // 重试配置参数
            RetryConfig retryConfig = new RetryConfig();
            retryConfig.setMaxRetries(2);
            retryConfig.setRetryDelay(1000);
            retryConfig.setTimeoutThreshold(60000);
            retryConfig.setRetryConnectionErrors(true);
            retryConfig.setConnectionRetryDelay(3000);

            // 流量配置参数
            TrafficConfig trafficConfig = new TrafficConfig();
            trafficConfig.setPattern(TrafficPattern.PEAK_MIDDLE);
            trafficConfig.setTotalDurationSeconds(60); // 1分钟
            trafficConfig.setPeakMultiplier(3);
            trafficConfig.setPeakRatio(0.2);

            log.info("📋 重试配置:");
            log.info("  最大重试次数: {}", retryConfig.getMaxRetries());
            log.info("  响应超时重试间隔: {}ms", retryConfig.getRetryDelay());
            log.info("  连接错误重试: {}", retryConfig.isRetryConnectionErrors() ? "启用" : "禁用");
            if (retryConfig.isRetryConnectionErrors()) {
                log.info("  连接错误重试间隔: {}ms", retryConfig.getConnectionRetryDelay());
            }
            log.info("  超时阈值: {}ms (超过此值不计入失败)", retryConfig.getTimeoutThreshold());

            log.info("\n📊 流量配置:");
            log.info("  流量模式: {}", trafficConfig.getPattern());
            log.info("  总持续时间: {}秒 ({}分钟)", trafficConfig.getTotalDurationSeconds(),
                    trafficConfig.getTotalDurationSeconds() / 60);
            log.info("  高峰倍数: {}", trafficConfig.getPeakMultiplier());
            log.info("  高峰比例: {}%", String.format("%.1f", trafficConfig.getPeakRatio() * 100));
            log.info("");

            // 加载所有问题
            List<String> questions = loadQuestions();
            if (questions.isEmpty()) {
                log.error("❌ 没有找到任何问题！");
                return;
            }

            // AgentClient 已通过 @Autowired 和 @CybotStarAgent 注入

            // 存储所有测试结果
            List<TestResult> results = new ArrayList<>();
            AtomicInteger completedCount = new AtomicInteger(0);

            try {
                log.info("🚀 开始压力测试（按自然流量波动分布）...\n");

                // 计算发送时间表
                List<Long> sendSchedule = calculateSendSchedule(questions.size(), trafficConfig);
                long testStartTime = System.currentTimeMillis();

                log.info("📅 请求发送计划:");
                log.info("  第一个请求: 0秒");
                if (!sendSchedule.isEmpty()) {
                    double lastRequestTime = sendSchedule.get(sendSchedule.size() - 1) / 1000.0;
                    double avgInterval = sendSchedule.size() > 1
                            ? (sendSchedule.get(sendSchedule.size() - 1) - sendSchedule.get(0))
                                    / (double) (sendSchedule.size() - 1) / 1000.0
                            : 0;
                    log.info("  最后一个请求: {}秒", String.format("%.1f", lastRequestTime));
                    log.info("  平均间隔: {}秒", String.format("%.2f", avgInterval));
                }
                log.info("");

                // 为每个问题创建独立的 session，按照时间表发送
                List<CompletableFuture<TestResult>> futures = new ArrayList<>();
                for (int i = 0; i < questions.size(); i++) {
                    final int index = i;
                    final long sendDelay = sendSchedule.get(i);

                    // 创建延迟发送的任务
                    CompletableFuture<TestResult> future = CompletableFuture
                            .supplyAsync(() -> {
                                // 等待到指定时间再发送
                                long currentTime = System.currentTimeMillis();
                                long waitTime = testStartTime + sendDelay - currentTime;
                                if (waitTime > 0) {
                                    try {
                                        Thread.sleep(waitTime);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        TestResult interruptedResult = new TestResult();
                                        interruptedResult.setQuestionIndex(index);
                                        interruptedResult.setQuestion(questions.get(index));
                                        interruptedResult.setSuccess(false);
                                        interruptedResult.setStatus(TestStatus.FAILED);
                                        interruptedResult.setError("线程被中断");
                                        return interruptedResult;
                                    }
                                }

                                // 发送请求
                                String sessionId = "stress-test-session-" + index;
                                return testQuestion(client, index, questions.get(index), sessionId, retryConfig)
                                        .join();
                            })
                            .thenApply(result -> {
                                synchronized (results) {
                                    results.add(result);
                                    int current = completedCount.incrementAndGet();
                                    printProgress(current, questions.size(), results);
                                }
                                return result;
                            });

                    futures.add(future);
                }

                // 等待所有请求完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // 换行，避免进度信息被覆盖
                System.out.println();

                // 打印统计结果
                printStatistics(results);

            } catch (Exception e) {
                log.error("\n❌ 压力测试过程中发生错误", e);
            } finally {
                // 关闭连接
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                client.close();
                log.info("\n✅ 测试完成，连接已关闭");
            }
        }
    }
}
