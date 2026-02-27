package com.brgroup.cybotstar.flow.handler;

import com.brgroup.cybotstar.flow.model.FlowData;
import com.brgroup.cybotstar.flow.model.FlowEventType;
import com.brgroup.cybotstar.flow.model.*;
import com.brgroup.cybotstar.core.model.ws.WSPayload;
import com.brgroup.cybotstar.core.model.ws.WSResponse;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flow 事件处理器容器
 * <p>
 * 统一管理所有 Flow 事件处理器，提供类型安全的注册和触发机制。
 * 支持简化版（MessageHandler）和完整版（FlowHandler）两种处理方式。
 * <p>
 * 使用示例：
 * <pre>
 * FlowHandlers handlers = new FlowHandlers();
 *
 * // 注册消息处理器（简化版，双参数）
 * handlers.onMessage((msg, isFinished) -&gt; {
 *     if (isFinished) {
 *         System.out.println("消息结束");
 *     } else {
 *         System.out.println(msg);
 *     }
 * });
 *
 * // 注册消息处理器（VO 版，单参数）
 * handlers.onMessageVO(vo -&gt; System.out.println(vo.getDisplayText()));
 *
 * // 注册启动处理器（原始数据）
 * handlers.onStart(data -&gt; System.out.println("Flow started"));
 *
 * // 注册启动处理器（VO 版）
 * handlers.onStartVO(vo -&gt; System.out.println("Started: " + vo.getFlowName()));
 * </pre>
 *
 * @author zhiyuan.xi
 */
public class FlowHandlers {

    /**
     * 处理器映射表：事件类型 -&gt; 处理器对象
     */
    @Getter
    private final Map<FlowEventType, Object> handlerMap = new ConcurrentHashMap<>();

    // ============================================================================
    // 消息事件
    // ============================================================================

    /**
     * 注册消息处理器（简化版，接收文本和完成状态）
     */
    public void onMessage(MessageHandler handler) {
        handlerMap.put(FlowEventType.MESSAGE, handler);
    }

    /**
     * 注册消息处理器（VO 版，接收结构化消息对象）
     */
    public void onMessageVO(FlowHandler<FlowMessageVO> handler) {
        handlerMap.put(FlowEventType.MESSAGE, handler);
    }

    /**
     * 注册消息处理器（完整数据版，接收原始 FlowData）
     */
    public void onMessageData(FlowHandler<FlowData> handler) {
        handlerMap.put(FlowEventType.MESSAGE, handler);
    }

    // ============================================================================
    // 启动事件
    // ============================================================================

    /**
     * 注册启动处理器（完整数据版）
     */
    public void onStart(FlowHandler<FlowData> handler) {
        handlerMap.put(FlowEventType.START, handler);
    }

    /**
     * 注册启动处理器（VO 版）
     */
    public void onStartVO(FlowHandler<FlowStartVO> handler) {
        handlerMap.put(FlowEventType.START, handler);
    }

    // ============================================================================
    // 结束事件
    // ============================================================================

    /**
     * 注册结束处理器（完整数据版）
     */
    public void onEnd(FlowHandler<FlowData> handler) {
        handlerMap.put(FlowEventType.END, handler);
    }

    /**
     * 注册结束处理器（VO 版）
     */
    public void onEndVO(FlowHandler<FlowEndVO> handler) {
        handlerMap.put(FlowEventType.END, handler);
    }

    // ============================================================================
    // 错误事件
    // ============================================================================

    /**
     * 注册错误处理器（完整数据版）
     */
    public void onError(FlowHandler<FlowData> handler) {
        handlerMap.put(FlowEventType.ERROR, handler);
    }

    /**
     * 注册错误处理器（VO 版）
     */
    public void onErrorVO(FlowHandler<FlowErrorVO> handler) {
        handlerMap.put(FlowEventType.ERROR, handler);
    }

    // ============================================================================
    // 等待输入事件
    // ============================================================================

    /**
     * 注册等待输入处理器（完整数据版）
     */
    public void onWaiting(FlowHandler<FlowData> handler) {
        handlerMap.put(FlowEventType.WAITING, handler);
    }

    /**
     * 注册等待输入处理器（VO 版）
     */
    public void onWaitingVO(FlowHandler<FlowWaitingVO> handler) {
        handlerMap.put(FlowEventType.WAITING, handler);
    }

    // ============================================================================
    // 节点进入事件
    // ============================================================================

    /**
     * 注册节点进入处理器（完整数据版）
     */
    public void onNodeEnter(FlowHandler<FlowData> handler) {
        handlerMap.put(FlowEventType.NODE_ENTER, handler);
    }

    /**
     * 注册节点进入处理器（VO 版）
     */
    public void onNodeEnterVO(FlowHandler<FlowNodeEnterVO> handler) {
        handlerMap.put(FlowEventType.NODE_ENTER, handler);
    }

    // ============================================================================
    // 跳转事件
    // ============================================================================

    /**
     * 注册跳转处理器（完整数据版）
     */
    public void onJump(FlowHandler<FlowData> handler) {
        handlerMap.put(FlowEventType.JUMP, handler);
    }

    /**
     * 注册跳转处理器（VO 版）
     */
    public void onJumpVO(FlowHandler<FlowJumpVO> handler) {
        handlerMap.put(FlowEventType.JUMP, handler);
    }

    // ============================================================================
    // 调试事件
    // ============================================================================

    /**
     * 注册调试处理器（完整数据版）
     */
    public void onDebug(FlowHandler<FlowData> handler) {
        handlerMap.put(FlowEventType.DEBUG, handler);
    }

    /**
     * 注册调试处理器（VO 版）
     */
    public void onDebugVO(FlowHandler<FlowDebugVO> handler) {
        handlerMap.put(FlowEventType.DEBUG, handler);
    }

    // ============================================================================
    // 连接事件
    // ============================================================================

    /**
     * 注册连接建立处理器
     */
    public void onConnected(Runnable handler) {
        handlerMap.put(FlowEventType.CONNECTED, handler);
    }

    /**
     * 注册连接断开处理器
     */
    public void onDisconnected(Runnable handler) {
        handlerMap.put(FlowEventType.DISCONNECTED, handler);
    }

    /**
     * 注册重连处理器
     */
    public void onReconnecting(FlowHandler<Integer> handler) {
        handlerMap.put(FlowEventType.RECONNECTING, handler);
    }

    // ============================================================================
    // 原始数据事件
    // ============================================================================

    /**
     * 注册原始请求处理器
     */
    public void onRawRequest(FlowHandler<WSPayload> handler) {
        handlerMap.put(FlowEventType.RAW_REQUEST, handler);
    }

    /**
     * 注册原始响应处理器
     */
    public void onRawResponse(FlowHandler<WSResponse> handler) {
        handlerMap.put(FlowEventType.RAW_RESPONSE, handler);
    }

    // ============================================================================
    // 移除处理器
    // ============================================================================

    public void offMessage() { handlerMap.remove(FlowEventType.MESSAGE); }
    public void offWaiting() { handlerMap.remove(FlowEventType.WAITING); }
    public void offEnd() { handlerMap.remove(FlowEventType.END); }
    public void offError() { handlerMap.remove(FlowEventType.ERROR); }
    public void offStart() { handlerMap.remove(FlowEventType.START); }
    public void offDebug() { handlerMap.remove(FlowEventType.DEBUG); }
    public void offNodeEnter() { handlerMap.remove(FlowEventType.NODE_ENTER); }
    public void offJump() { handlerMap.remove(FlowEventType.JUMP); }
    public void offConnected() { handlerMap.remove(FlowEventType.CONNECTED); }
    public void offDisconnected() { handlerMap.remove(FlowEventType.DISCONNECTED); }
    public void offReconnecting() { handlerMap.remove(FlowEventType.RECONNECTING); }
    public void offRawRequest() { handlerMap.remove(FlowEventType.RAW_REQUEST); }
    public void offRawResponse() { handlerMap.remove(FlowEventType.RAW_RESPONSE); }

    /**
     * 清除所有处理器
     */
    public void clear() {
        handlerMap.clear();
    }
}
