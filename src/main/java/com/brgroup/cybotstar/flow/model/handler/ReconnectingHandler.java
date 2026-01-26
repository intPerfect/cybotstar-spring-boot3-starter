package com.brgroup.cybotstar.flow.model.handler;

/**
 * Flow 重连事件处理器
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface ReconnectingHandler {
    /**
     * 处理重连事件
     *
     * @param attempt 重连尝试次数
     */
    void handle(Integer attempt);
}
