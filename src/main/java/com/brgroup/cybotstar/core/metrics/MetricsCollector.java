package com.brgroup.cybotstar.core.metrics;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标收集器
 * 收集系统运行指标
 *
 * @author zhiyuan.xi
 */
@Data
public class MetricsCollector {

    // 请求计数
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);

    // 连接计数
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong failedConnections = new AtomicLong(0);

    // 重连计数
    private final AtomicLong reconnectAttempts = new AtomicLong(0);
    private final AtomicLong successfulReconnects = new AtomicLong(0);

    // 消息计数
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);

    // 错误计数
    private final AtomicLong timeoutErrors = new AtomicLong(0);
    private final AtomicLong connectionErrors = new AtomicLong(0);
    private final AtomicLong validationErrors = new AtomicLong(0);

    // 启动时间
    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * 记录请求
     */
    public void recordRequest(boolean success) {
        totalRequests.incrementAndGet();
        if (success) {
            successfulRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }
    }

    /**
     * 记录连接
     */
    public void recordConnection(boolean success) {
        totalConnections.incrementAndGet();
        if (success) {
            activeConnections.incrementAndGet();
        } else {
            failedConnections.incrementAndGet();
        }
    }

    /**
     * 记录连接关闭
     */
    public void recordConnectionClosed() {
        activeConnections.decrementAndGet();
    }

    /**
     * 记录重连
     */
    public void recordReconnect(boolean success) {
        reconnectAttempts.incrementAndGet();
        if (success) {
            successfulReconnects.incrementAndGet();
        }
    }

    /**
     * 记录消息
     */
    public void recordMessage(long bytes) {
        totalMessages.incrementAndGet();
        totalBytes.addAndGet(bytes);
    }

    /**
     * 记录超时错误
     */
    public void recordTimeoutError() {
        timeoutErrors.incrementAndGet();
    }

    /**
     * 记录连接错误
     */
    public void recordConnectionError() {
        connectionErrors.incrementAndGet();
    }

    /**
     * 记录验证错误
     */
    public void recordValidationError() {
        validationErrors.incrementAndGet();
    }

    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) successfulRequests.get() / total * 100;
    }

    /**
     * 获取平均消息大小
     */
    public long getAverageMessageSize() {
        long messages = totalMessages.get();
        if (messages == 0) {
            return 0;
        }
        return totalBytes.get() / messages;
    }

    /**
     * 重置所有指标
     */
    public void reset() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        totalConnections.set(0);
        failedConnections.set(0);
        reconnectAttempts.set(0);
        successfulReconnects.set(0);
        totalMessages.set(0);
        totalBytes.set(0);
        timeoutErrors.set(0);
        connectionErrors.set(0);
        validationErrors.set(0);
    }

    /**
     * 获取指标摘要
     */
    public String getSummary() {
        return String.format(
            "Metrics Summary - Requests: %d (success: %.2f%%), " +
            "Connections: %d (active: %d, failed: %d), " +
            "Messages: %d (avg size: %d bytes), " +
            "Errors: timeout=%d, connection=%d, validation=%d",
            totalRequests.get(), getSuccessRate(),
            totalConnections.get(), activeConnections.get(), failedConnections.get(),
            totalMessages.get(), getAverageMessageSize(),
            timeoutErrors.get(), connectionErrors.get(), validationErrors.get()
        );
    }
}
