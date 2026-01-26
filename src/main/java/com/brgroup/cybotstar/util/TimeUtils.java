package com.brgroup.cybotstar.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * 时间工具类
 * 提供时间相关的工具函数，如睡眠、请求间隔控制等
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class TimeUtils {

    /**
     * 睡眠函数
     *
     * @param ms 等待时间（毫秒）
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> sleep(long ms) {
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Sleep interrupted", e);
            }
        });
    }

    /**
     * 请求间隔控制
     * 确保请求之间有足够的间隔
     *
     * @param lastRequestEndTime 上次请求结束时间
     * @param minInterval        最小间隔时间（毫秒）
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> requestIntervalControl(long lastRequestEndTime, long minInterval) {
        if (lastRequestEndTime > 0) {
            long timeSinceLastRequest = System.currentTimeMillis() - lastRequestEndTime;
            if (timeSinceLastRequest < minInterval) {
                long waitTime = minInterval - timeSinceLastRequest;
                return sleep(waitTime);
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}

