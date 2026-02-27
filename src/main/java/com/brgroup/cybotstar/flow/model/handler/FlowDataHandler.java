package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.FlowData;

/**
 * Flow 数据处理器别名接口
 * <p>
 * 为 {@link FlowHandler}&lt;FlowData&gt; 提供语义化别名，用于处理携带完整 FlowData 的事件。
 * <p>
 * 适用事件：START, END, ERROR, WAITING, NODE_ENTER, JUMP, DEBUG
 * <p>
 * 使用示例：
 * <pre>
 * // 使用别名接口（语义清晰）
 * flowClient.onStart((FlowDataHandler) data -&gt; System.out.println("Started"));
 *
 * // 或直接使用 FlowHandler（推荐）
 * flowClient.onStart(data -&gt; System.out.println("Started"));
 * </pre>
 *
 * @author zhiyuan.xi
 * @see FlowHandler
 */
@FunctionalInterface
public interface FlowDataHandler extends FlowHandler<FlowData> {
}
