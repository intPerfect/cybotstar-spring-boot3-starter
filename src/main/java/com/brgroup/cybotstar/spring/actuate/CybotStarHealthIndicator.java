package com.brgroup.cybotstar.spring.actuate;

import com.brgroup.cybotstar.core.health.HealthCheckResult;
import com.brgroup.cybotstar.core.health.HealthCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CybotStarHealthIndicator implements HealthIndicator {

    private final HealthCheckService healthCheckService;

    private final String agentName;

    public CybotStarHealthIndicator(HealthCheckService healthCheckService, String agentName) {
        this.healthCheckService = healthCheckService;
        this.agentName = agentName;
    }

    @Override
    public Health health() {
        try {
            HealthCheckResult result = healthCheckService.check();
            Health.Builder builder = new Health.Builder();

            switch (result.getStatus()) {
                case HEALTHY -> builder.up();
                case DEGRADED -> builder.status("DEGRADED");
                case UNHEALTHY -> builder.down();
            }

            Map<String, Object> details = new HashMap<>();
            if (result.getDetails() != null) {
                details.putAll(result.getDetails());
            }
            if (result.getMessage() != null) {
                details.put("message", result.getMessage());
            }
            details.put("agentName", agentName);

            return builder.withDetails(details).build();
        } catch (Exception e) {
            log.error("Health check failed for agent: {}", agentName, e);
            return Health.down()
                    .withDetail("agentName", agentName)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
