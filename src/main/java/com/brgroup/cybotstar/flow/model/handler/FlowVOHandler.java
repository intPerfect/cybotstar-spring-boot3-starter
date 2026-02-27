package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.vo.*;

/**
 * Flow VO 处理器别名接口集合
 * <p>
 * 为各种 VO 类型的处理器提供语义化别名接口。
 * 这些接口继承自 {@link FlowHandler}，保持向后兼容。
 * <p>
 * 使用示例：
 * <pre>
 * // 使用别名接口（语义清晰）
 * flowClient.onMessageVO(vo -&gt; System.out.println(vo.getDisplayText()));
 *
 * // 或使用 FlowHandler&lt;FlowMessageVO&gt;（推荐）
 * handlers.onMessageVO(vo -&gt; System.out.println(vo.getDisplayText()));
 * </pre>
 *
 * @author zhiyuan.xi
 * @see FlowHandler
 */
public final class FlowVOHandler {

    private FlowVOHandler() {
        // 工具类，禁止实例化
    }

    /**
     * 消息 VO 处理器
     */
    @FunctionalInterface
    public interface Message extends FlowHandler<FlowMessageVO> {
    }

    /**
     * 启动 VO 处理器
     */
    @FunctionalInterface
    public interface Start extends FlowHandler<FlowStartVO> {
    }

    /**
     * 结束 VO 处理器
     */
    @FunctionalInterface
    public interface End extends FlowHandler<FlowEndVO> {
    }

    /**
     * 错误 VO 处理器
     */
    @FunctionalInterface
    public interface Error extends FlowHandler<FlowErrorVO> {
    }

    /**
     * 等待输入 VO 处理器
     */
    @FunctionalInterface
    public interface Waiting extends FlowHandler<FlowWaitingVO> {
    }

    /**
     * 节点进入 VO 处理器
     */
    @FunctionalInterface
    public interface NodeEnter extends FlowHandler<FlowNodeEnterVO> {
    }

    /**
     * 跳转 VO 处理器
     */
    @FunctionalInterface
    public interface Jump extends FlowHandler<FlowJumpVO> {
    }

    /**
     * 调试 VO 处理器
     */
    @FunctionalInterface
    public interface Debug extends FlowHandler<FlowDebugVO> {
    }
}
