package com.brgroup.cybotstar.flow;

import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.config.FlowConfig;
import com.brgroup.cybotstar.connection.SingleConnectionManager;
import com.brgroup.cybotstar.connection.WebSocketConnection;
import com.brgroup.cybotstar.flow.model.FlowData;
import com.brgroup.cybotstar.flow.model.FlowEventType;
import com.brgroup.cybotstar.flow.model.FlowState;
import com.brgroup.cybotstar.flow.model.handler.*;
import com.brgroup.cybotstar.flow.model.vo.FlowStartVO;
import com.brgroup.cybotstar.flow.model.vo.FlowNodeEnterVO;
import com.brgroup.cybotstar.flow.model.vo.FlowMessageVO;
import com.brgroup.cybotstar.flow.model.vo.FlowWaitingVO;
import com.brgroup.cybotstar.flow.model.vo.FlowEndVO;
import com.brgroup.cybotstar.flow.model.vo.FlowErrorVO;
import com.brgroup.cybotstar.flow.model.vo.FlowDebugVO;
import com.brgroup.cybotstar.flow.model.vo.FlowJumpVO;
import com.brgroup.cybotstar.flow.util.FlowVOExtractor;
import com.brgroup.cybotstar.handler.GenericErrorHandler;
import com.brgroup.cybotstar.flow.exception.FlowException;
import com.brgroup.cybotstar.model.common.ConnectionState;
import com.brgroup.cybotstar.model.common.ResponseType;
import com.brgroup.cybotstar.model.ws.WSPayload;
import com.brgroup.cybotstar.model.ws.WSResponse;
import com.brgroup.cybotstar.util.payload.FlowPayloadBuilder;
import com.brgroup.cybotstar.flow.util.FlowUtils;
import com.brgroup.cybotstar.util.Constants;
import com.alibaba.fastjson2.JSON;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Flow 运行时引擎（Flow Runtime Engine）
 * <p>
 * 核心定位：FlowClient = 状态机 + 事件源
 * <p>
 * 1. 状态管理：维护 Flow 的生命周期状态
 * 2. 事件分发：将服务端消息转换为结构化事件
 * 3. 消息顺序：保证消息的顺序和完成信号
 * 4. 独立于 UI 层：不依赖具体的 UI 实现，适用于多种场景
 * 5. IO 抽象：基于生产者和消费者模式，支持各类输入输出方式
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class FlowClient {

    /**
     * 客户端配置
     */
    @Getter
    private final FlowConfig config;

    /**
     * Flow 内部状态
     */
    private FlowState flowState = FlowState.IDLE;

    /**
     * 连接管理器
     */
    private final SingleConnectionManager connectionManager;

    /**
     * 错误处理器
     */
    private final GenericErrorHandler errorHandler;

    /**
     * 类型化事件监听器存储
     */
    private final Map<FlowEventType, Object> typedHandlerMap = new ConcurrentHashMap<>();

    /**
     * 带 FlowData 的类型化事件监听器存储
     */
    private final Map<FlowEventType, Object> typedHandlerWithDataMap = new ConcurrentHashMap<>();

    /**
     * 完成 Future（用于 done() 方法）
     */
    private CompletableFuture<Void> completionFuture;

    /**
     * 中止原因
     */
    private String abortReason;

    /**
     * 消息处理器
     */
    private WebSocketConnection.WSMessageHandler messageHandler;

    /**
     * 会话 ID（在整个 Flow 对话中保持一致）
     */
    @Getter
    private String sessionId;

    /**
     * 是否已经处理过恢复会话的history提取（避免重复提取）
     */
    private boolean historyExtracted = false;

    /**
     * 构造方法
     *
     * @param config Flow 配置
     */
    public FlowClient(FlowConfig config) {
        this.config = config;

        // 初始化连接管理器
        AgentConfig properties = AgentConfig.builder()
                .credentials(config.getCredentials())
                .websocket(config.getWebsocket())
                .build();
        this.connectionManager = new SingleConnectionManager(properties);
        this.errorHandler = new GenericErrorHandler();

        // 监听连接状态变化（使用新的 API，忽略 sessionId 参数）
        this.connectionManager.registerStateChangeCallback((sessionId, state) -> handleConnectionStateChange(state));
    }

    // ============================================================================
    // 类型化事件订阅方法（使用 onMessage, onWaiting 等命名，避免歧义，IDE 提示更清晰）
    // ============================================================================

    /**
     * 订阅消息事件（简化版，接收消息文本和完成状态）
     *
     * @param handler 消息处理器（接收 String msg, boolean isFinished）
     */
    public void onMessage(MessageHandler handler) {
        typedHandlerMap.put(FlowEventType.MESSAGE, handler);
    }

    /**
     * 订阅消息事件（接收 VO）
     *
     * @param handler 消息处理器（接收 FlowMessageVO）
     */
    public void onMessage(MessageHandlerVO handler) {
        typedHandlerMap.put(FlowEventType.MESSAGE, handler);
    }

    /**
     * 订阅消息事件（接收 FlowData）
     *
     * @param handler 消息处理器（接收 FlowData）
     */
    public void onMessage(MessageHandlerWithData handler) {
        typedHandlerMap.put(FlowEventType.MESSAGE, handler);
    }

    /**
     * 订阅等待输入事件（接收 VO）
     *
     * @param handler 等待处理器（接收 FlowWaitingVO）
     */
    public void onWaiting(WaitingHandlerVO handler) {
        typedHandlerMap.put(FlowEventType.WAITING, handler);
    }

    /**
     * 订阅等待输入事件（接收 FlowData）
     *
     * @param handler 等待处理器（接收 FlowData）
     */
    public void onWaiting(WaitingHandler handler) {
        typedHandlerMap.put(FlowEventType.WAITING, handler);
    }

    /**
     * 订阅结束事件（接收 VO）
     *
     * @param handler 结束处理器（接收 FlowEndVO）
     */
    public void onEnd(EndHandlerVO handler) {
        typedHandlerMap.put(FlowEventType.END, handler);
    }

    /**
     * 订阅结束事件（接收 FlowData）
     *
     * @param handler 结束处理器（接收 FlowData）
     */
    public void onEnd(EndHandler handler) {
        typedHandlerMap.put(FlowEventType.END, handler);
    }

    /**
     * 订阅错误事件（接收 VO）
     *
     * @param handler 错误处理器（接收 FlowErrorVO）
     */
    public void onError(ErrorHandlerVO handler) {
        typedHandlerMap.put(FlowEventType.ERROR, handler);
    }

    /**
     * 订阅错误事件（接收 FlowData）
     *
     * @param handler 错误处理器（接收 FlowData）
     */
    public void onError(ErrorHandler handler) {
        typedHandlerMap.put(FlowEventType.ERROR, handler);
    }

    /**
     * 订阅启动事件
     *
     * @param handler 启动处理器
     */
    public void onStart(StartHandler handler) {
        typedHandlerMap.put(FlowEventType.START, handler);
    }

    /**
     * 订阅调试信息事件（接收 VO）
     *
     * @param handler 调试处理器（接收 FlowDebugVO）
     */
    public void onDebug(DebugHandlerVO handler) {
        if (config.getFlow() == null || !Boolean.TRUE.equals(config.getFlow().getOpenFlowDebug())) {
            log.warn("onDebug handler registered but open-flow-debug is not enabled. " +
                    "Please set 'cybotstar.flows.<name>.flow.open-flow-debug: true' in your configuration.");
            return;
        }
        typedHandlerMap.put(FlowEventType.DEBUG, handler);
    }

    /**
     * 订阅调试信息事件（接收 FlowData）
     *
     * @param handler 调试处理器（接收 FlowData）
     */
    public void onDebug(DebugHandler handler) {
        if (config.getFlow() == null || !Boolean.TRUE.equals(config.getFlow().getOpenFlowDebug())) {
            log.warn("onDebug handler registered but open-flow-debug is not enabled. " +
                    "Please set 'cybotstar.flows.<name>.flow.open-flow-debug: true' in your configuration.");
            return;
        }
        typedHandlerMap.put(FlowEventType.DEBUG, handler);
    }

    /**
     * 订阅节点进入事件
     *
     * @param handler 节点进入处理器
     */
    public void onNodeEnter(NodeEnterHandler handler) {
        typedHandlerMap.put(FlowEventType.NODE_ENTER, handler);
    }

    /**
     * 订阅节点进入事件（接收 VO）
     *
     * @param handler 节点进入处理器（接收 FlowNodeEnterVO）
     */
    public void onNodeEnter(NodeEnterHandlerVO handler) {
        typedHandlerMap.put(FlowEventType.NODE_ENTER, handler);
    }

    /**
     * 订阅跳转事件（接收 VO）
     *
     * @param handler 跳转处理器（接收 FlowJumpVO）
     */
    public void onJump(JumpHandlerVO handler) {
        typedHandlerMap.put(FlowEventType.JUMP, handler);
    }

    /**
     * 订阅跳转事件（接收 FlowData）
     *
     * @param handler 跳转处理器（接收 FlowData）
     */
    public void onJump(JumpHandler handler) {
        typedHandlerMap.put(FlowEventType.JUMP, handler);
    }

    /**
     * 订阅启动事件（接收 VO）
     *
     * @param handler 启动处理器（接收 FlowStartVO）
     */
    public void onStart(StartHandlerVO handler) {
        typedHandlerMap.put(FlowEventType.START, handler);
    }

    /**
     * 订阅原始响应事件
     *
     * @param handler 原始响应处理器
     */
    public void onRawResponse(RawResponseHandler handler) {
        typedHandlerMap.put(FlowEventType.RAW_RESPONSE, handler);
    }

    /**
     * 订阅原始请求事件
     *
     * @param handler 原始请求处理器
     */
    public void onRawRequest(RawRequestHandler handler) {
        typedHandlerMap.put(FlowEventType.RAW_REQUEST, handler);
    }

    /**
     * 订阅连接建立事件
     *
     * @param handler 连接建立处理器
     */
    public void onConnected(ConnectedHandler handler) {
        typedHandlerMap.put(FlowEventType.CONNECTED, handler);
    }

    /**
     * 订阅连接断开事件
     *
     * @param handler 连接断开处理器
     */
    public void onDisconnected(DisconnectedHandler handler) {
        typedHandlerMap.put(FlowEventType.DISCONNECTED, handler);
    }

    /**
     * 订阅重连事件
     *
     * @param handler 重连处理器
     */
    public void onReconnecting(ReconnectingHandler handler) {
        typedHandlerMap.put(FlowEventType.RECONNECTING, handler);
    }

    // ============================================================================
    // 取消订阅方法
    // ============================================================================

    /**
     * 取消订阅消息事件
     *
     * @param handler 消息处理器（可选，保留参数以保持 API 兼容性，但会被忽略）
     */
    /**
     * 取消订阅消息事件（清除所有消息处理器）
     */
    public void offMessage() {
        typedHandlerMap.remove(FlowEventType.MESSAGE);
    }

    /**
     * 取消订阅等待输入事件（清除所有等待处理器）
     */
    public void offWaiting() {
        typedHandlerMap.remove(FlowEventType.WAITING);
    }

    /**
     * 取消订阅结束事件（清除所有结束处理器）
     */
    public void offEnd() {
        typedHandlerMap.remove(FlowEventType.END);
    }

    /**
     * 取消订阅错误事件（清除所有错误处理器）
     */
    public void offError() {
        typedHandlerMap.remove(FlowEventType.ERROR);
    }

    /**
     * 触发事件
     *
     * @param event 事件类型
     * @param args  事件参数
     */
    private void emit(FlowEventType event, Object... args) {
        // 调用类型化处理器
        Object handler = typedHandlerMap.get(event);
        log.debug("emit called for event: {}, handler: {}, args length: {}", event, handler, args.length);
        if (handler != null) {
            try {
                invokeTypedHandler(event, handler, args);
            } catch (Exception e) {
                log.error("Error in typed {} handler", event, e);
            }
        } else {
            log.debug("No handler registered for event: {}", event);
        }
    }

    /**
     * 触发事件（带 FlowData）
     *
     * @param event    事件类型
     * @param flowData Flow 响应数据
     * @param args     事件参数
     */
    private void emit(FlowEventType event, FlowData flowData, Object... args) {
        // 优先调用带 FlowData 的处理器
        Object handlerWithData = typedHandlerWithDataMap.get(event);
        if (handlerWithData != null) {
            try {
                invokeTypedHandlerWithData(event, handlerWithData, flowData, args);
                return; // 如果带 FlowData 的处理器存在，就不调用旧的处理器
            } catch (Exception e) {
                log.error("Error in typed {} handler with data", event, e);
            }
        }

        // 如果没有带 FlowData 的处理器，调用旧的处理器
        Object handler = typedHandlerMap.get(event);
        if (handler != null) {
            try {
                invokeTypedHandler(event, handler, args);
            } catch (Exception e) {
                log.error("Error in typed {} handler", event, e);
            }
        }
    }

    /**
     * 调用类型化处理器
     *
     * @param event   事件类型
     * @param handler 处理器实例
     * @param args    事件参数
     */
    private void invokeTypedHandler(FlowEventType event, Object handler, Object... args) {
        switch (event) {
            case MESSAGE:
                log.debug("MESSAGE handler invoked, handler type: {}, args length: {}", handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof MessageHandler && args.length >= 2
                        && args[0] instanceof String && args[1] instanceof Boolean) {
                    log.debug("Calling MessageHandler.handle()");
                    ((MessageHandler) handler).handle((String) args[0], (Boolean) args[1]);
                } else if (handler instanceof MessageHandlerVO && args.length > 0 && args[0] instanceof FlowMessageVO) {
                    log.debug("Calling MessageHandlerVO.handle()");
                    ((MessageHandlerVO) handler).handle((FlowMessageVO) args[0]);
                } else if (handler instanceof MessageHandlerWithData && args.length > 0
                        && args[0] instanceof FlowData) {
                    log.debug("Calling MessageHandlerWithData.handle()");
                    ((MessageHandlerWithData) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for MESSAGE event - handler: {}, args types: {}",
                            handler.getClass().getName(),
                            args.length > 0
                                    ? java.util.Arrays
                                            .toString(java.util.Arrays.stream(args).map(Object::getClass).toArray())
                                    : "null");
                }
                break;
            case WAITING:
                log.debug("WAITING handler invoked, handler type: {}, args length: {}", handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof WaitingHandlerVO && args.length > 0 && args[0] instanceof FlowWaitingVO) {
                    log.debug("Calling WaitingHandlerVO.handle()");
                    ((WaitingHandlerVO) handler).handle((FlowWaitingVO) args[0]);
                } else if (handler instanceof WaitingHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling WaitingHandler.handle()");
                    ((WaitingHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for WAITING event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            case END:
                log.debug("END handler invoked, handler type: {}, args length: {}", handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof EndHandlerVO && args.length > 0 && args[0] instanceof FlowEndVO) {
                    log.debug("Calling EndHandlerVO.handle()");
                    ((EndHandlerVO) handler).handle((FlowEndVO) args[0]);
                } else if (handler instanceof EndHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling EndHandler.handle()");
                    ((EndHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for END event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            case ERROR:
                log.debug("ERROR handler invoked, handler type: {}, args length: {}", handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof ErrorHandlerVO && args.length > 0 && args[0] instanceof FlowErrorVO) {
                    log.debug("Calling ErrorHandlerVO.handle()");
                    ((ErrorHandlerVO) handler).handle((FlowErrorVO) args[0]);
                } else if (handler instanceof ErrorHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling ErrorHandler.handle()");
                    ((ErrorHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for ERROR event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            case START:
                log.debug("START handler invoked, handler type: {}, args length: {}", handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof StartHandlerVO && args.length > 0 && args[0] instanceof FlowStartVO) {
                    log.debug("Calling StartHandlerVO.handle()");
                    ((StartHandlerVO) handler).handle((FlowStartVO) args[0]);
                } else if (handler instanceof StartHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling StartHandler.handle()");
                    ((StartHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for START event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            case DEBUG:
                log.debug("DEBUG handler invoked, handler type: {}, args length: {}", handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof DebugHandlerVO && args.length > 0 && args[0] instanceof FlowDebugVO) {
                    log.debug("Calling DebugHandlerVO.handle()");
                    ((DebugHandlerVO) handler).handle((FlowDebugVO) args[0]);
                } else if (handler instanceof DebugHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling DebugHandler.handle()");
                    ((DebugHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for DEBUG event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            case NODE_ENTER:
                log.debug("NODE_ENTER handler invoked, handler type: {}, args length: {}", handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof NodeEnterHandlerVO && args.length > 0 && args[0] instanceof FlowNodeEnterVO) {
                    log.debug("Calling NodeEnterHandlerVO.handle()");
                    ((NodeEnterHandlerVO) handler).handle((FlowNodeEnterVO) args[0]);
                } else if (handler instanceof NodeEnterHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling NodeEnterHandler.handle()");
                    ((NodeEnterHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for NODE_ENTER event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            case JUMP:
                log.debug("JUMP handler invoked, handler type: {}, args length: {}", handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof JumpHandlerVO && args.length > 0 && args[0] instanceof FlowJumpVO) {
                    log.debug("Calling JumpHandlerVO.handle()");
                    ((JumpHandlerVO) handler).handle((FlowJumpVO) args[0]);
                } else if (handler instanceof JumpHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling JumpHandler.handle()");
                    ((JumpHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for JUMP event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            case RAW_RESPONSE:
                if (handler instanceof RawResponseHandler && args.length > 0 && args[0] instanceof WSResponse) {
                    ((RawResponseHandler) handler).handle((WSResponse) args[0]);
                }
                break;
            case RAW_REQUEST:
                if (handler instanceof RawRequestHandler && args.length > 0 && args[0] instanceof WSPayload) {
                    ((RawRequestHandler) handler).handle((WSPayload) args[0]);
                }
                break;
            case CONNECTED:
                if (handler instanceof ConnectedHandler) {
                    ((ConnectedHandler) handler).handle();
                }
                break;
            case DISCONNECTED:
                if (handler instanceof DisconnectedHandler) {
                    ((DisconnectedHandler) handler).handle();
                }
                break;
            case RECONNECTING:
                if (handler instanceof ReconnectingHandler && args.length > 0 && args[0] instanceof Integer) {
                    ((ReconnectingHandler) handler).handle((Integer) args[0]);
                }
                break;
            default:
                break;
        }
    }

    /**
     * 调用类型化处理器（带 FlowData）
     *
     * @param event    事件类型
     * @param handler  处理器实例
     * @param flowData Flow 响应数据
     * @param args     事件参数
     */
    private void invokeTypedHandlerWithData(FlowEventType event, Object handler, FlowData flowData, Object... args) {
        switch (event) {
            case MESSAGE:
                log.debug("MESSAGE handler invoked (withData), handler type: {}, args length: {}",
                        handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof MessageHandler && args.length >= 2
                        && args[0] instanceof String && args[1] instanceof Boolean) {
                    log.debug("Calling MessageHandler.handle()");
                    ((MessageHandler) handler).handle((String) args[0], (Boolean) args[1]);
                } else if (handler instanceof MessageHandlerVO && args.length > 0 && args[0] instanceof FlowMessageVO) {
                    log.debug("Calling MessageHandlerVO.handle()");
                    ((MessageHandlerVO) handler).handle((FlowMessageVO) args[0]);
                } else if (handler instanceof MessageHandlerWithData && args.length > 0
                        && args[0] instanceof FlowData) {
                    log.debug("Calling MessageHandlerWithData.handle()");
                    ((MessageHandlerWithData) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for MESSAGE event - handler: {}, args types: {}",
                            handler.getClass().getName(),
                            args.length > 0
                                    ? java.util.Arrays
                                            .toString(java.util.Arrays.stream(args).map(Object::getClass).toArray())
                                    : "null");
                }
                break;
            case WAITING:
                log.debug("WAITING handler invoked (withData), handler type: {}, args length: {}",
                        handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof WaitingHandlerVO && args.length > 0 && args[0] instanceof FlowWaitingVO) {
                    log.debug("Calling WaitingHandlerVO.handle()");
                    ((WaitingHandlerVO) handler).handle((FlowWaitingVO) args[0]);
                } else if (handler instanceof WaitingHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling WaitingHandler.handle()");
                    ((WaitingHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for WAITING event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            case END:
                log.debug("END handler invoked (withData), handler type: {}, args length: {}",
                        handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof EndHandlerVO && args.length > 0 && args[0] instanceof FlowEndVO) {
                    log.debug("Calling EndHandlerVO.handle()");
                    ((EndHandlerVO) handler).handle((FlowEndVO) args[0]);
                } else if (handler instanceof EndHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling EndHandler.handle()");
                    ((EndHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for END event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            case ERROR:
                log.debug("ERROR handler invoked (withData), handler type: {}, args length: {}",
                        handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof ErrorHandlerVO && args.length > 0 && args[0] instanceof FlowErrorVO) {
                    log.debug("Calling ErrorHandlerVO.handle()");
                    ((ErrorHandlerVO) handler).handle((FlowErrorVO) args[0]);
                } else if (handler instanceof ErrorHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling ErrorHandler.handle()");
                    ((ErrorHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for ERROR event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            // START case 已在 invokeTypedHandler 中处理，这里不再需要
            case DEBUG:
                log.debug("DEBUG handler invoked (withData), handler type: {}, args length: {}",
                        handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof DebugHandlerVO && args.length > 0 && args[0] instanceof FlowDebugVO) {
                    log.debug("Calling DebugHandlerVO.handle()");
                    ((DebugHandlerVO) handler).handle((FlowDebugVO) args[0]);
                } else if (handler instanceof DebugHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling DebugHandler.handle()");
                    ((DebugHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for DEBUG event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            case NODE_ENTER:
                log.debug("NODE_ENTER handler invoked, handler type: {}, args length: {}", handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof NodeEnterHandlerVO && args.length > 0 && args[0] instanceof FlowNodeEnterVO) {
                    log.debug("Calling NodeEnterHandlerVO.handle()");
                    ((NodeEnterHandlerVO) handler).handle((FlowNodeEnterVO) args[0]);
                } else if (handler instanceof NodeEnterHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling NodeEnterHandler.handle()");
                    ((NodeEnterHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for NODE_ENTER event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            case JUMP:
                log.debug("JUMP handler invoked (withData), handler type: {}, args length: {}",
                        handler.getClass().getName(),
                        args.length);
                // 根据 handler 类型和参数类型匹配调用
                if (handler instanceof JumpHandlerVO && args.length > 0 && args[0] instanceof FlowJumpVO) {
                    log.debug("Calling JumpHandlerVO.handle()");
                    ((JumpHandlerVO) handler).handle((FlowJumpVO) args[0]);
                } else if (handler instanceof JumpHandler && args.length > 0 && args[0] instanceof FlowData) {
                    log.debug("Calling JumpHandler.handle()");
                    ((JumpHandler) handler).handle((FlowData) args[0]);
                } else {
                    log.debug("No matching handler found for JUMP event - handler: {}, args[0] type: {}",
                            handler.getClass().getName(),
                            args.length > 0 ? args[0].getClass().getName() : "null");
                }
                break;
            default:
                // 其他事件类型不支持带 FlowData 的处理器
                break;
        }
    }

    // ============================================================================
    // 状态管理（Pull 模型）
    // ============================================================================

    /**
     * 获取当前状态
     */
    public FlowState getState() {
        return flowState;
    }

    /**
     * 等待 Flow 完成
     * <p>
     * 返回一个 CompletableFuture，当 Flow 完成（end 事件）或出错（error 事件）时
     * complete/completeExceptionally。
     * 如果已经设置了 onEnd 或 onError 回调，它们会正常触发，不会被覆盖。
     *
     * @return CompletableFuture，Flow 完成时 complete，出错时 completeExceptionally
     */
    public CompletableFuture<Void> done() {
        // 如果还没有创建 Future，创建一个
        if (completionFuture == null || completionFuture.isDone()) {
            completionFuture = new CompletableFuture<>();
        }
        return completionFuture;
    }

    // ============================================================================
    // 控制方法（Push 模型）
    // ============================================================================

    /**
     * 连接建立的 Future（用于 FlowClientService.connect()）
     */
    private CompletableFuture<Void> connectionFuture;

    /**
     * 启动 Flow 并等待连接建立
     * <p>
     * sessionId 会自动生成 UUID。
     *
     * @param initialInput 初始输入
     * @return sessionId 会话 ID
     * @throws FlowException 连接失败或超时时抛出异常
     * @example ```java
     *          FlowClient flowClient = new FlowClient(config);
     *          <p>
     *          // 使用默认超时和自动生成的 UUID
     *          String sessionId = flowClient.start("初始输入");
     *          System.out.println("Session ID: " + sessionId);
     *          <p>
     *          // 指定超时时间
     *          String sessionId2 = flowClient.start("初始输入", 5000);
     *          ```
     */
    public String start(String initialInput) {
        return start(initialInput, Constants.DEFAULT_RESPONSE_TIMEOUT);
    }

    /**
     * 启动 Flow 并等待连接建立（指定超时时间）
     *
     * @param initialInput  初始输入
     * @param timeoutMillis 超时时间（毫秒）
     * @return sessionId 会话 ID
     * @throws FlowException 连接失败或超时时抛出异常
     */
    public String start(String initialInput, long timeoutMillis) {
        // 如果没有 sessionId，自动生成 UUID
        if (this.sessionId == null || this.sessionId.isEmpty()) {
            this.sessionId = UUID.randomUUID().toString();
        }

        return startInternal(initialInput, timeoutMillis);
    }

    /**
     * 从指定的 sessionId 启动 Flow 并等待连接建立
     * <p>
     * 此方法用于恢复之前的会话，会使用指定的 sessionId。
     * 恢复会话时通常不需要传递初始输入。
     *
     * @param sessionId 会话 ID（用于恢复之前的会话）
     * @return sessionId 会话 ID
     * @throws FlowException 连接失败或超时时抛出异常
     * @example ```java
     *          FlowClient flowClient = new FlowClient(config);
     *          <p>
     *          // 恢复之前的会话（使用默认超时，不传递初始输入）
     *          String sessionId = flowClient.startFrom("之前的sessionId");
     *          System.out.println("Session ID: " + sessionId);
     *          <p>
     *          // 恢复之前的会话并指定超时
     *          String sessionId2 = flowClient.startFrom("之前的sessionId", 5000);
     *          <p>
     *          // 恢复会话并传递初始输入（可选）
     *          String sessionId3 = flowClient.startFrom("之前的sessionId", 5000,
     *          "继续对话");
     *          ```
     */
    public String startFrom(String sessionId) {
        return startFrom(sessionId, Constants.DEFAULT_RESPONSE_TIMEOUT);
    }

    /**
     * 从指定的 sessionId 启动 Flow 并等待连接建立（指定超时时间）
     *
     * @param sessionId     会话 ID（用于恢复之前的会话）
     * @param timeoutMillis 超时时间（毫秒）
     * @return sessionId 会话 ID
     * @throws FlowException 连接失败或超时时抛出异常
     */
    public String startFrom(String sessionId, long timeoutMillis) {
        return startFrom(sessionId, timeoutMillis, "");
    }

    /**
     * 从指定的 sessionId 启动 Flow 并等待连接建立（指定超时时间和初始输入）
     *
     * @param sessionId     会话 ID（用于恢复之前的会话）
     * @param timeoutMillis 超时时间（毫秒）
     * @param initialInput  初始输入（可选）
     * @return sessionId 会话 ID
     * @throws FlowException 连接失败或超时时抛出异常
     */
    public String startFrom(String sessionId, long timeoutMillis, String initialInput) {
        // 使用提供的 sessionId（用于恢复会话）
        this.sessionId = sessionId;

        return startInternal(initialInput != null ? initialInput : "", timeoutMillis);
    }

    /**
     * 内部启动方法（公共逻辑）
     *
     * @param initialInput  初始输入
     * @param timeoutMillis 超时时间（毫秒）
     * @return sessionId 会话 ID
     * @throws FlowException 连接失败或超时时抛出异常
     */
    private String startInternal(String initialInput, long timeoutMillis) {
        final String finalInitialInput = initialInput != null ? initialInput : "";

        flowState = FlowState.STARTING;
        historyExtracted = false; // 重置history提取标志

        // 建立连接
        WebSocketConnection connection = connectionManager.getConnection();

        // 移除旧的消息处理器（如果存在）
        if (messageHandler != null) {
            connection.removeMessageHandler(messageHandler);
        }
        // 注册新的消息处理器
        messageHandler = response -> {
            try {
                handleMessage(response);
            } catch (Exception e) {
                errorHandler.handle(e, GenericErrorHandler.withContext(Map.of("method", "start.onMessage")));
            }
        };
        connection.onMessage(messageHandler);

        // 创建完成 Future（如果还没有创建或已完成）
        if (completionFuture == null || completionFuture.isDone()) {
            completionFuture = new CompletableFuture<>();
        }

        // 创建连接建立的 Future
        connectionFuture = new CompletableFuture<>();

        // 异步启动连接（不阻塞，立即返回）
        connectionManager.connect().thenRun(() -> {
            flowState = FlowState.RUNNING;

            // 构造 Flow 载荷（使用存储的 sessionId）
            FlowConfig flowConfig = FlowConfig.builder()
                    .credentials(config.getCredentials())
                    .flow(config.getFlow())
                    .question(finalInitialInput)
                    .build();

            AgentConfig properties = AgentConfig.builder()
                    .credentials(config.getCredentials())
                    .build();

            WSPayload payload = FlowPayloadBuilder.buildFlowPayload(properties, flowConfig, this.sessionId);

            // 触发原始请求事件
            emit(FlowEventType.RAW_REQUEST, payload);

            // 发送初始请求
            connection.send(payload);

            // 完成连接建立的 Future
            connectionFuture.complete(null);
        }).exceptionally(error -> {
            log.error("Flow startup failed", error);
            // 如果连接失败，reject completionFuture 和 connectionFuture
            FlowException flowError = error instanceof FlowException
                    ? (FlowException) error
                    : FlowException.connectionFailed(error.getMessage(),
                            error instanceof Exception ? error : null);
            if (completionFuture != null && !completionFuture.isDone()) {
                completionFuture.completeExceptionally(flowError);
            }
            if (connectionFuture != null && !connectionFuture.isDone()) {
                connectionFuture.completeExceptionally(flowError);
            }
            return null;
        });

        // 阻塞等待连接建立（必须等待）
        try {
            connectionFuture
                    .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                    .exceptionally(throwable -> {
                        // 处理超时异常
                        if (throwable instanceof TimeoutException) {
                            throw FlowException.connectionTimeout(timeoutMillis);
                        }
                        // 如果已经是 FlowException，直接抛出
                        if (throwable instanceof FlowException) {
                            throw (FlowException) throwable;
                        }
                        // 其他异常包装为 RuntimeException
                        if (throwable instanceof RuntimeException) {
                            throw (RuntimeException) throwable;
                        }
                        throw new RuntimeException(throwable);
                    })
                    .join(); // 阻塞等待完成
        } catch (Exception e) {
            // 将异常转换为 FlowException（如果还不是）
            if (e instanceof FlowException) {
                throw (FlowException) e;
            }
            if (e.getCause() != null && e.getCause() instanceof FlowException) {
                throw (FlowException) e.getCause();
            }
            // 如果是超时异常，转换为 FlowException
            // 注意：orTimeout() 超时时会抛出 CompletionException，TimeoutException 作为 cause
            if (e.getCause() != null && e.getCause() instanceof TimeoutException) {
                throw FlowException.connectionTimeout(timeoutMillis);
            }
            // 其他异常包装为 FlowException
            throw FlowException.connectionFailed("Connection establishment failed: " + e.getMessage(), e);
        }

        // 连接建立后返回 sessionId
        return this.sessionId;
    }

    /**
     * 获取连接建立的 Future（用于 FlowClientService.connect()）
     *
     * @return CompletableFuture，连接建立时完成
     */
    public CompletableFuture<Void> getConnectionFuture() {
        return connectionFuture != null ? connectionFuture : CompletableFuture.completedFuture(null);
    }

    /**
     * 发送用户输入
     *
     * @param input 用户输入
     * @return CompletableFuture
     */
    public CompletableFuture<Void> send(String input) {
        FlowState currentState = getState();
        if (currentState != FlowState.RUNNING && currentState != FlowState.WAITING) {
            FlowException error = FlowException.notRunning();
            log.warn("Flow send failed: not running, currentState: {}, isRunning: {}, isWaiting: {}",
                    flowState, currentState == FlowState.RUNNING || currentState == FlowState.WAITING, currentState == FlowState.WAITING);
            emit(FlowEventType.ERROR, error); // 先触发用户回调
            errorHandler.handle(error,
                    GenericErrorHandler.withContext(Map.of("method", "send", "reason", "notRunning")));

            // 然后 completeExceptionally completionFuture（如果存在）
            if (completionFuture != null && !completionFuture.isDone()) {
                completionFuture.completeExceptionally(error);
            }
            throw error;
        }

        if (currentState != FlowState.WAITING) {
            FlowException error = FlowException.notWaiting();
            log.warn("Flow send failed: not waiting for input, currentState: {}, isRunning: {}, isWaiting: {}",
                    flowState, currentState == FlowState.RUNNING || currentState == FlowState.WAITING, currentState == FlowState.WAITING);
            emit(FlowEventType.ERROR, error); // 先触发用户回调
            errorHandler.handle(error,
                    GenericErrorHandler.withContext(Map.of("method", "send", "reason", "notWaiting")));

            // 然后 completeExceptionally completionFuture（如果存在）
            if (completionFuture != null && !completionFuture.isDone()) {
                completionFuture.completeExceptionally(error);
            }
            throw error;
        }

        flowState = FlowState.RUNNING;

        // 构造载荷（使用存储的 sessionId）
        FlowConfig flowConfig = FlowConfig.builder()
                .credentials(config.getCredentials())
                .flow(config.getFlow())
                .question(input)
                .build();

        AgentConfig properties = AgentConfig.builder()
                .credentials(config.getCredentials())
                .build();

        WSPayload payload = FlowPayloadBuilder.buildFlowPayload(properties, flowConfig, this.sessionId);

        // 触发原始请求事件
        emit(FlowEventType.RAW_REQUEST, payload);

        // 通过连接管理器发送消息
        WebSocketConnection connection = connectionManager.get();
        if (connection != null && connection.isConnected()) {
            connection.send(payload);
            return CompletableFuture.completedFuture(null);
        } else {
            FlowException error = FlowException.connectionClosed();
            emit(FlowEventType.ERROR, error); // 先触发用户回调
            errorHandler.handle(error,
                    GenericErrorHandler.withContext(Map.of("method", "send", "reason", "connectionClosed")));

            // 然后 completeExceptionally completionFuture（如果存在）
            if (completionFuture != null && !completionFuture.isDone()) {
                completionFuture.completeExceptionally(error);
            }
            throw error;
        }
    }

    /**
     * 中止 Flow
     *
     * @param reason 中止原因
     */
    public void abort(String reason) {
        flowState = FlowState.ABORTED;
        abortReason = reason;

        // 创建中止错误并 completeExceptionally Promise
        FlowException error = FlowException.flowError(reason != null ? reason : "Flow 被中止", null);
        if (completionFuture != null && !completionFuture.isDone()) {
            completionFuture.completeExceptionally(error);
        }

        // 关闭 WebSocket 连接
        connectionManager.disconnect();
    }

    /**
     * 关闭 Flow 对话
     */
    public void close() {
        flowState = FlowState.COMPLETED;

        // 关闭 WebSocket 连接
        connectionManager.disconnect();
    }

    // ============================================================================
    // 连接状态处理
    // ============================================================================

    /**
     * 处理连接状态变化
     *
     * @param state 连接状态
     */
    private void handleConnectionStateChange(ConnectionState state) {
        switch (state) {
            case CONNECTED:
                emit(FlowEventType.CONNECTED);
                break;
            case DISCONNECTED:
            case CLOSED:
                emit(FlowEventType.DISCONNECTED);
                break;
            case RECONNECTING:
                emit(FlowEventType.RECONNECTING, 0);
                break;
            default:
                break;
        }
    }

    // ============================================================================
    // 内部消息处理
    // ============================================================================

    /**
     * 处理 WebSocket 消息
     *
     * @param response WebSocket 响应
     */
    private void handleMessage(WSResponse response) {
        // 对于 START 事件（002000），即使 Flow 还未完全启动也要处理
        // 因为 START 事件可能在连接建立时就已经发送
        FlowData.MessageData messageData = response.getData() instanceof Map
                ? JSON.parseObject(JSON.toJSONString(response.getData()), FlowData.MessageData.class)
                : null;
        String dataCode = messageData != null && messageData.getCode() != null ? messageData.getCode() : "";
        String topLevelCode = response.getCode();
        boolean isStartEvent = "002000".equals(dataCode) || "002000".equals(topLevelCode);

        FlowState currentState = getState();
        boolean isStarted = currentState == FlowState.RUNNING || currentState == FlowState.WAITING || currentState == FlowState.STARTING;
        log.debug("handleMessage called, isStarted: {}, isStartEvent: {}, dataCode: {}, topLevelCode: {}",
                isStarted, isStartEvent, dataCode, topLevelCode);

        if (!isStarted && !isStartEvent) {
            log.debug("Message ignored: Flow not started and not START event");
            return;
        }

        // 忽略心跳
        if (ResponseType.isType(response.getType(), ResponseType.HEARTBEAT)) {
            return;
        }

        // 调用原始消息回调，让用户可以获取完整响应
        emit(FlowEventType.RAW_RESPONSE, response);

        // 首先检查是否有顶层的错误 code（不在 data 中）
        if (topLevelCode != null && topLevelCode.startsWith("4")) {
            String errorMessage = response.getMessage() != null ? response.getMessage() : "Unknown error";
            FlowException error = new FlowException(topLevelCode, errorMessage, Map.of("response", response));
            flowState = FlowState.ERROR;
            emit(FlowEventType.ERROR, error); // 先触发用户回调
            errorHandler.handle(error,
                    GenericErrorHandler.withContext(Map.of("method", "handleMessage", "topLevelCode", topLevelCode)));

            // 然后 completeExceptionally completionFuture（如果存在）
            if (completionFuture != null && !completionFuture.isDone()) {
                completionFuture.completeExceptionally(error);
            }
            return;
        }

        // 检测服务端返回的业务错误（如 "系统异常(209),稍后再试"）
        Object dataObj = response.getData();
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = (Map<String, Object>) dataObj;
            String answer = (String) responseData.getOrDefault("answer", "");
            // 检测包含异常关键字的错误信息，自动提取错误码
            if (answer.contains("系统异常(")) {
                FlowException error = FlowException.fromServerMessage(answer, response);
                flowState = FlowState.ERROR;
                emit(FlowEventType.ERROR, error); // 先触发用户回调
                errorHandler.handle(error,
                        GenericErrorHandler.withContext(Map.of("method", "handleMessage", "hasSystemError", true)));

                // 然后 completeExceptionally completionFuture（如果存在）
                if (completionFuture != null && !completionFuture.isDone()) {
                    completionFuture.completeExceptionally(error);
                }
                return;
            }
            // 检测风控拦截
            if (answer.contains("涉及到风险")) {
                FlowException error = FlowException.flowError("Risk control blocked: " + answer, response);
                flowState = FlowState.ABORTED;
                abortReason = answer;
                emit(FlowEventType.ERROR, error);
                errorHandler.handle(error,
                        GenericErrorHandler.withContext(Map.of("method", "handleMessage", "riskBlocked", true)));
                if (completionFuture != null && !completionFuture.isDone()) {
                    completionFuture.completeExceptionally(error);
                }
                return;
            }
        }

        // 所有消息都尝试作为 Flow 消息处理
        handleFlowMessage(response);
    }

    /**
     * 处理 Flow 消息
     *
     * @param response WebSocket 响应
     */
    private void handleFlowMessage(WSResponse response) {
        // 当 data 为纯字符串时（如风控拦截），无法解析为 FlowData.MessageData
        // 需要特殊处理：构建一个简化的 FlowData，将字符串放入 message 事件
        // 当 data 为纯字符串时（如风控拦截），无法解析为 FlowData.MessageData
        if (response.getData() instanceof String) {
            boolean isFinished = "y".equalsIgnoreCase(response.getFinish());
            String textContent = (String) response.getData();

            // 检测风控拦截
            if (textContent != null && textContent.contains("涉及到风险")) {
                FlowException error = FlowException.flowError("Risk control blocked: " + textContent, response);
                flowState = FlowState.ABORTED;
                abortReason = textContent;
                emit(FlowEventType.ERROR, error);
                errorHandler.handle(error,
                        GenericErrorHandler.withContext(Map.of("method", "handleFlowMessage", "riskBlocked", true)));
                if (completionFuture != null && !completionFuture.isDone()) {
                    completionFuture.completeExceptionally(error);
                }
                return;
            }

            // 服务端对字符串消息会发两帧：finish="n" 带文本，finish="y" 重复文本
            // 只在 finish="n" 时发送内容，finish="y" 时发送空字符串作为完成信号，避免重复
            Object handler = typedHandlerMap.get(FlowEventType.MESSAGE);
            if (handler instanceof MessageHandler) {
                emit(FlowEventType.MESSAGE, isFinished ? "" : textContent, isFinished);
            } else if (handler instanceof MessageHandlerVO) {
                FlowMessageVO vo = new FlowMessageVO();
                vo.setDisplayText(isFinished ? "" : textContent);
                vo.setFinished(isFinished);
                emit(FlowEventType.MESSAGE, vo);
            }

            // 风控拦截等字符串响应完成后，恢复 WAITING 状态，允许用户重新输入
            if (isFinished && flowState == FlowState.RUNNING) {
                flowState = FlowState.WAITING;
            }
            return;
        }

        // data 为对象时，正常解析
        FlowData flowData = JSON.parseObject(JSON.toJSONString(response), FlowData.class);

        if (flowData == null) {
            log.debug("Failed to parse Flow response");
            return;
        }

        // 获取 data.code（如果存在），否则使用顶层 code
        FlowData.MessageData messageData = flowData.getData();
        String code = "";
        if (messageData != null && messageData.getCode() != null) {
            code = messageData.getCode();
        } else if (flowData.getCode() != null) {
            code = flowData.getCode();
        }

        log.debug("Processing Flow message code: {}, currentState: {}", code, flowState);

        // 使用枚举方法查找对应的 FlowEventType
        FlowEventType eventType = FlowEventType.fromEventCode(code);
        log.debug("Event type resolved: {}", eventType);
        if (eventType != null) {
            switch (eventType) {
                case START:
                    log.debug("Calling handleFlowStart");
                    handleFlowStart(flowData);
                    break;

                case END:
                    handleFlowEnd(flowData);
                    break;

                case NODE_ENTER:
                    handleNodeEnter(flowData);
                    break;

                case DEBUG:
                    handleDebugMessage(flowData);
                    break;

                case WAITING:
                    handleWaiting(flowData);
                    break;

                case JUMP:
                    handleJump(flowData);
                    break;

                case SUCCESS:
                    handleSuccessResponse(flowData);
                    break;

                case ROUND_COMPLETE:
                    handleRoundComplete(flowData);
                    break;

                case ERROR:
                    handleFlowError(flowData);
                    break;

                default:
                    // 其他事件类型，忽略
                    break;
            }
        }
    }

    /**
     * 处理 Flow 开始事件
     */
    private void handleFlowStart(FlowData flowData) {
        Object handler = typedHandlerMap.get(FlowEventType.START);
        log.debug("handleFlowStart called, handler registered: {}, handler type: {}",
                handler != null, handler != null ? handler.getClass().getName() : "null");

        if (handler == null) {
            return;
        }

        // 根据注册的 handler 类型决定传递什么参数
        if (handler instanceof StartHandlerVO) {
            // 如果注册的是 StartHandlerVO，提取并传递 FlowStartVO
            FlowStartVO flowStartVO = FlowVOExtractor.extractFlowStartVO(flowData);
            log.debug("FlowStartVO extracted: {}", flowStartVO);
            emit(FlowEventType.START, flowStartVO);
        } else if (handler instanceof StartHandler) {
            // 如果注册的是 StartHandler，传递完整的 FlowData
            log.debug("Passing FlowData to StartHandler");
            emit(FlowEventType.START, flowData);
        } else {
            log.warn("Unknown handler type for START event: {}", handler.getClass().getName());
        }
    }

    /**
     * 处理 Flow 结束事件（002001）
     * <p>
     * 注意：002005（轮次结束）不是flow完成，只有002001（flow退出）才是真正的flow完成。
     */
    private void handleFlowEnd(FlowData flowData) {
        flowState = FlowState.COMPLETED;

        // 提取 finalText（从 data.answer 或 data.output.robot_user_replying）
        String finalText = "";
        FlowData.MessageData messageData = flowData.getData();
        if (messageData != null) {
            if (messageData.getAnswer() != null && !messageData.getAnswer().isEmpty()) {
                finalText = messageData.getAnswer();
            } else if (messageData.getOutput() != null && messageData.getOutput().getRobotUserReplying() != null) {
                finalText = messageData.getOutput().getRobotUserReplying();
            }
        }

        Object handler = typedHandlerMap.get(FlowEventType.END);
        log.debug("handleFlowEnd called, handler registered: {}, handler type: {}",
                handler != null, handler != null ? handler.getClass().getName() : "null");

        if (handler != null) {
            // 根据注册的 handler 类型决定传递什么参数
            if (handler instanceof EndHandlerVO) {
                // 如果注册的是 EndHandlerVO，提取并传递 FlowEndVO
                FlowEndVO flowEndVO = FlowVOExtractor.extractFlowEndVO(flowData, finalText);
                log.debug("FlowEndVO extracted: {}", flowEndVO);
                emit(FlowEventType.END, flowEndVO);
            } else if (handler instanceof EndHandler) {
                // 如果注册的是 EndHandler，传递完整的 FlowData
                log.debug("Passing FlowData to EndHandler");
                emit(FlowEventType.END, flowData);
            } else {
                log.warn("Unknown handler type for END event: {}", handler.getClass().getName());
            }
        }

        // 然后 complete completionFuture（如果存在）
        if (completionFuture != null && !completionFuture.isDone()) {
            completionFuture.complete(null);
        }
    }

    /**
     * 处理消息事件
     */
    private void handleMessage(FlowData flowData) {
        Object handler = typedHandlerMap.get(FlowEventType.MESSAGE);
        log.debug("handleMessage called, handler registered: {}, handler type: {}",
                handler != null, handler != null ? handler.getClass().getName() : "null");

        if (handler == null) {
            return;
        }

        FlowData.MessageData messageData = flowData.getData();

        // 根据注册的 handler 类型决定传递什么参数
        if (handler instanceof MessageHandler) {
            // 如果注册的是 MessageHandler（简化版），传递消息文本和完成状态
            String msg = FlowUtils.extractFlowDisplayText(messageData);
            if (msg == null) {
                msg = "";
            }
            boolean isFinished = FlowUtils.isMessageFinished(messageData);
            log.debug("Passing simplified message to MessageHandler: msg={}, isFinished={}", msg, isFinished);
            emit(FlowEventType.MESSAGE, msg, isFinished);
        } else if (handler instanceof MessageHandlerVO) {
            // 如果注册的是 MessageHandlerVO，提取并传递 FlowMessageVO
            FlowMessageVO flowMessageVO = FlowVOExtractor.extractFlowMessageVO(flowData);
            log.debug("FlowMessageVO extracted: {}", flowMessageVO);
            emit(FlowEventType.MESSAGE, flowMessageVO);
        } else if (handler instanceof MessageHandlerWithData) {
            // 如果注册的是 MessageHandlerWithData，传递完整的 FlowData
            log.debug("Passing FlowData to MessageHandlerWithData");
            emit(FlowEventType.MESSAGE, flowData);
        } else {
            log.warn("Unknown handler type for MESSAGE event: {}", handler.getClass().getName());
        }
    }

    /**
     * 处理节点进入事件（002002）
     */
    private void handleNodeEnter(FlowData flowData) {
        Object handler = typedHandlerMap.get(FlowEventType.NODE_ENTER);
        log.debug("handleNodeEnter called, handler registered: {}, handler type: {}",
                handler != null, handler != null ? handler.getClass().getName() : "null");

        if (handler == null) {
            return;
        }

        FlowData.MessageData messageData = flowData.getData();
        if (messageData == null) {
            return;
        }

        // 根据注册的 handler 类型决定传递什么参数
        if (handler instanceof NodeEnterHandlerVO) {
            // 如果注册的是 NodeEnterHandlerVO，提取并传递 FlowNodeEnterVO
            FlowNodeEnterVO flowNodeEnterVO = FlowVOExtractor.extractFlowNodeEnterVO(flowData);
            log.debug("FlowNodeEnterVO extracted: {}", flowNodeEnterVO);
            emit(FlowEventType.NODE_ENTER, flowNodeEnterVO);
        } else if (handler instanceof NodeEnterHandler) {
            // 如果注册的是 NodeEnterHandler，传递完整的 FlowData
            log.debug("Passing FlowData to NodeEnterHandler");
            emit(FlowEventType.NODE_ENTER, flowData);
        } else {
            log.warn("Unknown handler type for NODE_ENTER event: {}", handler.getClass().getName());
        }
    }

    /**
     * 处理调试信息
     */
    private void handleDebugMessage(FlowData flowData) {
        FlowData.MessageData messageData = flowData.getData();
        if (messageData == null) {
            return;
        }
        String answer = messageData.getAnswer() != null ? messageData.getAnswer() : "";
        if (answer != null && !answer.contains("entity:") && !answer.contains("node_id:")) {
            Object handler = typedHandlerMap.get(FlowEventType.DEBUG);
            log.debug("handleDebugMessage called, handler registered: {}, handler type: {}",
                    handler != null, handler != null ? handler.getClass().getName() : "null");

            if (handler != null) {
                // 根据注册的 handler 类型决定传递什么参数
                if (handler instanceof DebugHandlerVO) {
                    // 如果注册的是 DebugHandlerVO，提取并传递 FlowDebugVO
                    FlowDebugVO flowDebugVO = FlowVOExtractor.extractFlowDebugVO(flowData, answer);
                    log.debug("FlowDebugVO extracted: {}", flowDebugVO);
                    emit(FlowEventType.DEBUG, flowDebugVO);
                } else if (handler instanceof DebugHandler) {
                    // 如果注册的是 DebugHandler，传递完整的 FlowData
                    log.debug("Passing FlowData to DebugHandler");
                    emit(FlowEventType.DEBUG, flowData);
                } else {
                    log.warn("Unknown handler type for DEBUG event: {}", handler.getClass().getName());
                }
            }
        }
    }

    /**
     * 处理等待用户输入事件（002004）
     */
    private void handleWaiting(FlowData flowData) {
        FlowData.MessageData messageData = flowData.getData();
        if (messageData == null) {
            return;
        }
        // 先提取并触发消息事件（优先从output，如果没有则从history）
        extractAndEmitMessage(messageData, flowData, FlowEventType.WAITING.getEventCode());

        // 然后触发WAITING事件
        if (flowState != FlowState.WAITING) {
            flowState = FlowState.WAITING;

            Object handler = typedHandlerMap.get(FlowEventType.WAITING);
            log.debug("handleWaiting called, handler registered: {}, handler type: {}",
                    handler != null, handler != null ? handler.getClass().getName() : "null");

            if (handler != null) {
                // 根据注册的 handler 类型决定传递什么参数
                if (handler instanceof WaitingHandlerVO) {
                    // 如果注册的是 WaitingHandlerVO，提取并传递 FlowWaitingVO
                    FlowWaitingVO flowWaitingVO = FlowVOExtractor.extractFlowWaitingVO(flowData);
                    log.debug("FlowWaitingVO extracted: {}", flowWaitingVO);
                    emit(FlowEventType.WAITING, flowWaitingVO);
                } else if (handler instanceof WaitingHandler) {
                    // 如果注册的是 WaitingHandler，传递完整的 FlowData
                    log.debug("Passing FlowData to WaitingHandler");
                    emit(FlowEventType.WAITING, flowData);
                } else {
                    log.warn("Unknown handler type for WAITING event: {}", handler.getClass().getName());
                }
            }
        }
    }

    /**
     * 处理轮次完成事件（002005）
     * <p>
     * 注意：002005只是"flow引擎当前运行轮次结束"，不是flow完成。
     * 只有002001（flow退出）才是真正的flow完成。
     * 因此这个方法只记录日志，不完成flow。
     */
    private void handleRoundComplete(FlowData flowData) {
        // 002005只是轮次结束，不意味着flow完成，只有002001（handleFlowEnd）才会真正完成flow
        // 这里不改变flow状态
        // 注意：此事件通常不需要触发用户回调，但如果需要可以在这里添加
    }

    /**
     * 处理跳转事件
     */
    private void handleJump(FlowData flowData) {
        String jumpType = extractJumpType();

        Object handler = typedHandlerMap.get(FlowEventType.JUMP);
        log.debug("handleJump called, handler registered: {}, handler type: {}",
                handler != null, handler != null ? handler.getClass().getName() : "null");

        if (handler != null) {
            // 根据注册的 handler 类型决定传递什么参数
            if (handler instanceof JumpHandlerVO) {
                // 如果注册的是 JumpHandlerVO，提取并传递 FlowJumpVO
                FlowJumpVO flowJumpVO = FlowVOExtractor.extractFlowJumpVO(flowData, jumpType);
                log.debug("FlowJumpVO extracted: {}", flowJumpVO);
                emit(FlowEventType.JUMP, flowJumpVO);
            } else if (handler instanceof JumpHandler) {
                // 如果注册的是 JumpHandler，传递完整的 FlowData
                log.debug("Passing FlowData to JumpHandler");
                emit(FlowEventType.JUMP, flowData);
            } else {
                log.warn("Unknown handler type for JUMP event: {}", handler.getClass().getName());
            }
        }
    }

    /**
     * 从history中提取最后一个Bot的问题（用于恢复会话场景）
     *
     * @param messageData Flow消息数据
     * @return 显示文本，如果未找到则返回null
     */
    private String extractMessageFromHistory(FlowData.MessageData messageData) {
        if (messageData == null) {
            return null;
        }

        List<Map<String, Object>> history = messageData.getHistory();
        if (history == null || history.isEmpty()) {
            return null;
        }

        // 从后往前查找最后一个有robot_user_asking或robot_user_replying的条目
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> historyItem = history.get(i);
            if (historyItem == null) {
                continue;
            }

            String robotUserAsking = (String) historyItem.get("robot_user_asking");
            String robotUserReplying = (String) historyItem.get("robot_user_replying");

            // 优先使用robot_user_asking，如果没有则使用robot_user_replying
            if (robotUserAsking != null && !robotUserAsking.trim().isEmpty()) {
                return robotUserAsking;
            } else if (robotUserReplying != null && !robotUserReplying.trim().isEmpty()) {
                return robotUserReplying;
            }
        }

        return null;
    }

    /**
     * 提取并触发消息事件（优先从output，如果没有则从history，但只在首次恢复时从history提取）
     *
     * @param messageData Flow消息数据
     * @param flowData    完整的 Flow 响应数据
     * @param context     上下文信息（用于日志）
     */
    private void extractAndEmitMessage(FlowData.MessageData messageData, FlowData flowData, String context) {
        // 先尝试从output中提取消息
        String displayText = FlowUtils.extractFlowDisplayText(messageData);
        Integer answerIndex = FlowUtils.getAnswerIndex(messageData);
        boolean isFinished = FlowUtils.isMessageFinished(messageData);

        if (displayText != null && !(isFinished && answerIndex != null)) {
            // 有消息内容，调用 handleMessage 方法
            log.debug("Triggering MESSAGE event from {}, displayText: {}, nodeId: {}",
                    context, displayText, messageData != null ? messageData.getCurNodeId() : null);
            handleMessage(flowData);
            // 有新的消息内容，标记已经进入正常流程，不再从history提取
            historyExtracted = true;
        } else {
            // output中没有消息，尝试从history中提取（仅在首次恢复时）
            if (!historyExtracted) {
                log.debug("No message content extracted from output in {}, checking history for recovery",
                        context);
                String historyDisplayText = extractMessageFromHistory(messageData);
                if (historyDisplayText != null) {
                    handleMessage(flowData);
                    historyExtracted = true; // 标记已经处理过恢复场景
                }
            }
        }
    }

    /**
     * 处理成功响应
     */
    private void handleSuccessResponse(FlowData flowData) {
        FlowData.MessageData messageData = flowData.getData();
        if (messageData == null) {
            return;
        }

        // 检查是否等待用户输入（通过 node_waiting_input 字段，在响应顶层）
        Integer nodeWaitingInput = flowData.getNodeWaitingInput();

        // 提取消息内容（优先提取，确保消息先显示）
        String displayText = FlowUtils.extractFlowDisplayText(messageData);
        Integer answerIndex = FlowUtils.getAnswerIndex(messageData);
        boolean isFinished = FlowUtils.isMessageFinished(messageData);

        // 先触发MESSAGE事件（如果有消息内容），确保Bot的问候消息先显示
        // 如果output中没有消息且需要等待输入，尝试从history中提取（恢复会话场景）
        if (displayText == null && nodeWaitingInput != null && nodeWaitingInput == 1) {
            extractAndEmitMessage(messageData, flowData, "000000"); // 成功响应 code，无对应 FlowEventType
        } else if (displayText != null && !(isFinished && answerIndex != null)) {
            // 有消息内容，调用 handleMessage 方法
            log.debug("Triggering MESSAGE event, displayText: {}, nodeId: {}",
                    displayText, messageData.getCurNodeId());
            handleMessage(flowData);
            // 有新的消息内容，标记已经进入正常流程，不再从history提取
            historyExtracted = true;
        }

        // 然后触发WAITING事件（如果需要等待用户输入）
        if (nodeWaitingInput != null && nodeWaitingInput == 1) {
            // 避免重复触发
            if (flowState != FlowState.WAITING) {
                flowState = FlowState.WAITING;
                emit(FlowEventType.WAITING, flowData);
            }
        }
    }

    /**
     * 处理 Flow 错误
     */
    private void handleFlowError(FlowData flowData) {
        // error message 在顶层 flowData.message
        String errorMessage = flowData.getMessage() != null ? flowData.getMessage() : "Flow execution error";
        FlowException error = FlowException.flowError(errorMessage, null);
        flowState = FlowState.ERROR;

        Object handler = typedHandlerMap.get(FlowEventType.ERROR);
        log.debug("handleFlowError called, handler registered: {}, handler type: {}",
                handler != null, handler != null ? handler.getClass().getName() : "null");

        if (handler != null) {
            // 根据注册的 handler 类型决定传递什么参数
            if (handler instanceof ErrorHandlerVO) {
                // 如果注册的是 ErrorHandlerVO，提取并传递 FlowErrorVO
                FlowErrorVO flowErrorVO = FlowVOExtractor.extractFlowErrorVO(flowData, error);
                log.debug("FlowErrorVO extracted: {}", flowErrorVO);
                emit(FlowEventType.ERROR, flowErrorVO);
            } else if (handler instanceof ErrorHandler) {
                // 如果注册的是 ErrorHandler，传递完整的 FlowData
                log.debug("Passing FlowData to ErrorHandler");
                emit(FlowEventType.ERROR, flowData);
            } else {
                log.warn("Unknown handler type for ERROR event: {}", handler.getClass().getName());
            }
        }

        errorHandler.handle(error, GenericErrorHandler.withContext(Map.of("method", "handleFlowError")));

        // 然后 completeExceptionally completionFuture（如果存在）
        if (completionFuture != null && !completionFuture.isDone()) {
            completionFuture.completeExceptionally(error);
        }
    }

    /**
     * 提取跳转类型
     *
     * @return 跳转类型
     */
    private String extractJumpType() {
        return "unknown";
    }
}
