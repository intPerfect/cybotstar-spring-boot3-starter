package com.brgroup.cybotstar.flow.model.handler;

/**
 * Flow 消息事件处理器（简化版）
 * <p>
 * 接收消息文本和完成状态，是最常用的消息处理方式。
 * 与 {@link FlowHandler} 不同，此接口接收两个参数，更适合简单的文本处理场景。
 * <p>
 * 使用示例：
 * <pre>
 * flowClient.onMessage((msg, isFinished) -&gt; {
 *     if (isFinished) {
 *         System.out.println("消息结束");
 *     } else {
 *         System.out.println("收到消息: " + msg);
 *     }
 * });
 * </pre>
 *
 * @author zhiyuan.xi
 * @see FlowHandler
 * @see FlowHandlers#onMessage(MessageHandler)
 */
@FunctionalInterface
public interface MessageHandler {
    /**
     * 处理消息事件
     *
     * @param msg        消息文本（完成时为空字符串）
     * @param isFinished 是否完成（true 表示本轮消息已结束）
     */
    void handle(String msg, boolean isFinished);
}
