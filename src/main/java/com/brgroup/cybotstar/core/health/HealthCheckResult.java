package com.brgroup.cybotstar.core.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查结果
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckResult {

    /**
     * 健康状态
     */
    public enum Status {
        HEALTHY,    // 健康
        DEGRADED,   // 降级
        UNHEALTHY   // 不健康
    }

    /**
     * 整体状态
     */
    private Status status;

    /**
     * 检查时间
     */
    private LocalDateTime timestamp;

    /**
     * 详细信息
     */
    private Map<String, Object> details;

    /**
     * 错误消息（如果有）
     */
    private String message;

    /**
     * 创建健康状态
     */
    public static HealthCheckResult healthy(Map<String, Object> details) {
        return HealthCheckResult.builder()
                .status(Status.HEALTHY)
                .timestamp(LocalDateTime.now())
                .details(details)
                .build();
    }

    /**
     * 创建降级状态
     */
    public static HealthCheckResult degraded(String message, Map<String, Object> details) {
        return HealthCheckResult.builder()
                .status(Status.DEGRADED)
                .timestamp(LocalDateTime.now())
                .message(message)
                .details(details)
                .build();
    }

    /**
     * 创建不健康状态
     */
    public static HealthCheckResult unhealthy(String message, Map<String, Object> details) {
        return HealthCheckResult.builder()
                .status(Status.UNHEALTHY)
                .timestamp(LocalDateTime.now())
                .message(message)
                .details(details)
                .build();
    }
}
