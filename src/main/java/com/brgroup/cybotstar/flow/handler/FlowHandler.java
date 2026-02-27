package com.brgroup.cybotstar.flow.handler;

/**
 * Flow 事件处理器统一接口
 * <p>
 * 所有 Flow 事件处理器都实现此接口，通过泛型参数指定处理的数据类型。
 * 这是一个函数式接口，可以使用 Lambda 表达式简化代码。
 * <p>
 * 使用示例：
 * <pre>
 * // 处理消息事件（VO 版）
 * FlowHandler&lt;FlowMessageVO&gt; messageHandler = vo -&gt;
 *     System.out.println(vo.getDisplayText());
 *
 * // 处理启动事件（原始数据）
 * FlowHandler&lt;FlowData&gt; startHandler = data -&gt;
 *     System.out.println("Started: " + data.getCode());
 *
 * // 处理错误事件（VO 版）
 * FlowHandler&lt;FlowErrorVO&gt; errorHandler = vo -&gt;
 *     System.err.println("Error: " + vo.getMessage());
 *
 * // 处理连接事件（无参数，使用 Runnable 更合适）
 * Runnable connectedHandler = () -&gt; System.out.println("Connected");
 * </pre>
 *
 * @param <T> 事件数据类型，可以是 FlowData、各种 VO 对象，或其他类型
 * @author zhiyuan.xi
 * @see MessageHandler 双参数的消息处理器
 * @see FlowHandlers 处理器容器类
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
