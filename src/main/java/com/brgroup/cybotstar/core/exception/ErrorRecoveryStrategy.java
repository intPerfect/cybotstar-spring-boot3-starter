package com.brgroup.cybotstar.core.exception;

import com.brgroup.cybotstar.agent.exception.AgentErrorCode;
import com.brgroup.cybotstar.agent.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Predicate;

/**
 * 错误恢复策略
 * 提供统一的错误恢复机制
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class ErrorRecoveryStrategy {

    /**
     * 可重试的错误判断
     */
    private static final Predicate<Throwable> RETRYABLE_ERROR = error -> {
        // 网络相关错误可重试
        if (error instanceof IOException) {
            return true;
        }

        // AgentException 中的连接相关错误可重试
        if (error instanceof AgentException agentEx) {
            return agentEx.getCode() == AgentErrorCode.CONNECTION_FAILED ||
                    agentEx.getCode() == AgentErrorCode.CONNECTION_TIMEOUT;
        }

        return false;
    };

    /**
     * 创建指数退避重试策略
     *
     * @param maxAttempts 最大重试次数
     * @param minBackoff  最小退避时间
     * @param maxBackoff  最大退避时间
     * @return Retry 策略
     */
    @NonNull
    public static Retry exponentialBackoff(int maxAttempts, Duration minBackoff, Duration maxBackoff) {
        return Retry.backoff(maxAttempts, minBackoff)
                .maxBackoff(maxBackoff)
                .filter(RETRYABLE_ERROR)
                .doBeforeRetry(signal ->
                        log.warn("Retrying operation, attempt: {}, error: {}",
                                signal.totalRetries() + 1,
                                signal.failure().getMessage()))
                .onRetryExhaustedThrow((spec, signal) -> {
                    log.error("Retry exhausted after {} attempts", signal.totalRetries());
                    return signal.failure();
                });
    }

    /**
     * 创建默认重试策略
     * 最多重试 3 次，初始退避 100ms，最大退避 5s
     */
    @NonNull
    public static Retry defaultRetry() {
        return exponentialBackoff(3, Duration.ofMillis(100), Duration.ofSeconds(5));
    }

    /**
     * 创建连接重试策略
     * 最多重试 5 次，初始退避 500ms，最大退避 10s
     */
    @NonNull
    public static Retry connectionRetry() {
        return exponentialBackoff(5, Duration.ofMillis(500), Duration.ofSeconds(10));
    }

    /**
     * 错误降级处理
     * 当错误不可恢复时，返回降级值
     *
     * @param fallbackValue 降级值
     * @param <T>           返回类型
     * @return 降级 Mono
     */
    @NonNull
    public static <T> Mono<T> fallback(@NonNull T fallbackValue) {
        return Mono.just(fallbackValue)
                .doOnNext(v -> log.debug("Using fallback value: {}", v));
    }

    /**
     * 错误降级处理（空值）
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <T> Mono<T> emptyFallback() {
        return (Mono<T>) Mono.empty()
                .doOnSuccess(v -> log.debug("Using empty fallback"));
    }

    /**
     * 判断错误是否可重试
     */
    public static boolean isRetryable(@NonNull Throwable error) {
        return RETRYABLE_ERROR.test(error);
    }

    /**
     * 判断错误是否致命（不可恢复）
     */
    public static boolean isFatal(@NonNull Throwable error) {
        // 配置错误是致命的
        if (error instanceof com.brgroup.cybotstar.agent.exception.AgentException) {
            com.brgroup.cybotstar.agent.exception.AgentException agentEx =
                    (com.brgroup.cybotstar.agent.exception.AgentException) error;
            return agentEx.getCode() == com.brgroup.cybotstar.agent.exception.AgentErrorCode.INVALID_CONFIG;
        }

        // 参数错误是致命的
        return error instanceof IllegalArgumentException ||
                error instanceof NullPointerException;
    }
}
