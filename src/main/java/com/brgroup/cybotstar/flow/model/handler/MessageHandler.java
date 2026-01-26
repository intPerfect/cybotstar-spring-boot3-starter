package com.brgroup.cybotstar.flow.model.handler;

/**
 * Flow 消息事件处理器（简化版）
 * 接收消息文本和完成状态，使用更方便
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface MessageHandler {
    /**
     * 处理消息事件
     *
     * @param msg        消息文本
     * @param isFinished 是否完成
     */
    void handle(String msg, boolean isFinished);
}
