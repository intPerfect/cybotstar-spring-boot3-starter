package com.brgroup.cybotstar.flow.model.handler;

/**
 * Flow 连接建立事件处理器
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface ConnectedHandler {
    /**
     * 处理连接建立事件
     */
    void handle();
}
