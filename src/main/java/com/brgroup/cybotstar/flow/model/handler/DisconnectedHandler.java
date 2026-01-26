package com.brgroup.cybotstar.flow.model.handler;

/**
 * Flow 连接断开事件处理器
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface DisconnectedHandler {
    /**
     * 处理连接断开事件
     */
    void handle();
}
