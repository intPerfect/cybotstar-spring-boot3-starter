package com.brgroup.cybotstar.core.health;

import com.brgroup.cybotstar.core.connection.ConnectionManager;
import com.brgroup.cybotstar.core.util.CybotStarConstants;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查服务
 * 提供系统健康状态检查
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class HealthCheckService {

    private final ConnectionManager connectionManager;

    public HealthCheckService(@NonNull ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * 执行健康检查
     */
    @NonNull
    public HealthCheckResult check() {
        Map<String, Object> details = new HashMap<>();

        try {
            // 检查连接池状态
            long cacheSize = connectionManager.getCacheSize();
            int activeConnections = connectionManager.getActiveConnectionCount();
            boolean limitReached = connectionManager.isConnectionLimitReached();

            details.put("cacheSize", cacheSize);
            details.put("activeConnections", activeConnections);
            details.put("maxSessions", CybotStarConstants.MAX_SESSION_COUNT);
            details.put("limitReached", limitReached);
            details.put("cacheStats", connectionManager.getCacheStats());

            // 计算使用率
            double usageRate = (double) cacheSize / CybotStarConstants.MAX_SESSION_COUNT;
            details.put("usageRate", String.format("%.2f%%", usageRate * 100));

            // 判断健康状态
            if (limitReached) {
                return HealthCheckResult.unhealthy(
                    "Connection limit reached",
                    details
                );
            } else if (usageRate > 0.8) {
                return HealthCheckResult.degraded(
                    "Connection pool usage high: " + String.format("%.2f%%", usageRate * 100),
                    details
                );
            } else {
                return HealthCheckResult.healthy(details);
            }

        } catch (Exception e) {
            log.error("Health check failed", e);
            details.put("error", e.getMessage());
            return HealthCheckResult.unhealthy(
                "Health check failed: " + e.getMessage(),
                details
            );
        }
    }

    /**
     * 快速健康检查（仅检查基本状态）
     */
    @NonNull
    public boolean isHealthy() {
        try {
            return !connectionManager.isConnectionLimitReached();
        } catch (Exception e) {
            log.error("Quick health check failed", e);
            return false;
        }
    }
}
