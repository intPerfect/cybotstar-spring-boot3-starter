package com.brgroup.cybotstar.flow.model.handler;

/**
 * Flow 事件处理器统一接口
 * <p>
 * 所有 Flow 事件处理器都实现此接口，通过泛型参数指定处理的数据类型。
 * <p>
 * 使用示例：
 * <pre>
 * // 处理消息事件（简化版）
 * FlowHandler&lt;String&gt; messageHandler = msg -&gt; System.out.println(msg);
 *
 * // 处理启动事件
 * FlowHandler&lt;FlowData&gt; startHandler = data -&gt; System.out.println("Started");
 *
 * // 处理连接事件（无参数）
 * FlowHandler&lt;Void&gt; connectedHandler = v -&gt; System.out.println("Connected");
 * </pre>
 *
 * @param <T> 事件数据类型
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface FlowHandler<T> {
    /**
     * 处理事件
     *
     * @param data 事件数据，对于无参数事件为 null
     */
    void handle(T data);
}
