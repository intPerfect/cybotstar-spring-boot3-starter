package com.brgroup.cybotstar.flow;

import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.config.FlowConfig;
import com.brgroup.cybotstar.connection.ConnectionManager;
import com.brgroup.cybotstar.flow.model.FlowData;
import com.brgroup.cybotstar.flow.model.FlowEventType;
import com.brgroup.cybotstar.flow.model.FlowState;
import com.brgroup.cybotstar.flow.model.handler.FlowHandler;
import com.brgroup.cybotstar.flow.model.handler.MessageHandler;
import com.brgroup.cybotstar.flow.model.vo.*;
import com.brgroup.cybotstar.flow.util.FlowVOExtractor;
import com.brgroup.cybotstar.flow.exception.FlowException;
import com.brgroup.cybotstar.model.common.ConnectionState;
import com.brgroup.cybotstar.model.common.ResponseType;
import com.brgroup.cybotstar.model.ws.WSPayload;
import com.brgroup.cybotstar.model.ws.WSResponse;
import com.brgroup.cybotstar.util.CybotStarConstants;
import com.brgroup.cybotstar.util.payload.FlowPayloadBuilder;
import com.brgroup.cybotstar.flow.util.FlowUtils;
import com.alibaba.fastjson2.JSON;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 响应式 Flow 运行时引擎
 * 完全基于 Project Reactor 的响应式实现
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class FlowClient {

    @Getter
    private final FlowConfig config;

    private final AtomicReference<FlowState> flowState = new AtomicReference<>(FlowState.IDLE);

    @NonNull
    private final ConnectionManager connectionManager;

    private final Map<FlowEventType, Object> typedHandlerMap = new ConcurrentHashMap<>();

    @Getter
    private final AtomicReference<String> sessionId = new AtomicReference<>();

    private final AtomicBoolean historyExtracted = new AtomicBoolean(false);

    private final AtomicReference<String> abortReason = new AtomicReference<>();

    // 完成信号 Sink
    private final Sinks.One<Void> completionSink = Sinks.one();

    // 全局错误处理器
    private volatile Consumer<Throwable> globalErrorHandler = e -> log.error("Unhandled error in FlowClient", e);

    public FlowClient(@NonNull FlowConfig config) {
        this.config = config;
        AgentConfig properties = AgentConfig.builder()
                .credentials(config.getCredentials())
                .websocket(config.getWebsocket())
                .build();
        this.connectionManager = new ConnectionManager(properties);
    }

    // ============================================================================
    // 事件订阅
    // ============================================================================

    // 消息事件（简化版：接收文本和完成状态）
    public void onMessage(MessageHandler handler) { typedHandlerMap.put(FlowEventType.MESSAGE, handler); }

    // 消息事件（VO 版：接收结构化消息对象）
    public void onMessage(FlowHandler<FlowMessageVO> handler) { typedHandlerMap.put(FlowEventType.MESSAGE, handler); }

    // 消息事件（完整数据版：接收原始 FlowData）
    public void onMessageData(FlowHandler<FlowData> handler) { typedHandlerMap.put(FlowEventType.MESSAGE, handler); }

    // 等待输入事件
    public void onWaiting(FlowHandler<FlowWaitingVO> handler) { typedHandlerMap.put(FlowEventType.WAITING, handler); }
    public void onWaitingData(FlowHandler<FlowData> handler) { typedHandlerMap.put(FlowEventType.WAITING, handler); }

    // 结束事件
    public void onEnd(FlowHandler<FlowEndVO> handler) { typedHandlerMap.put(FlowEventType.END, handler); }
    public void onEndData(FlowHandler<FlowData> handler) { typedHandlerMap.put(FlowEventType.END, handler); }

    // 错误事件
    public void onError(FlowHandler<FlowErrorVO> handler) { typedHandlerMap.put(FlowEventType.ERROR, handler); }
    public void onErrorData(FlowHandler<FlowData> handler) { typedHandlerMap.put(FlowEventType.ERROR, handler); }

    // 启动事件
    public void onStart(FlowHandler<FlowStartVO> handler) { typedHandlerMap.put(FlowEventType.START, handler); }
    public void onStartData(FlowHandler<FlowData> handler) { typedHandlerMap.put(FlowEventType.START, handler); }

    // 调试事件
    public void onDebug(FlowHandler<FlowDebugVO> handler) {
        if (config.getFlow() == null || !Boolean.TRUE.equals(config.getFlow().getOpenFlowDebug())) {
            log.warn("onDebug handler registered but open-flow-debug is not enabled.");
            return;
        }
        typedHandlerMap.put(FlowEventType.DEBUG, handler);
    }
    public void onDebugData(FlowHandler<FlowData> handler) {
        if (config.getFlow() == null || !Boolean.TRUE.equals(config.getFlow().getOpenFlowDebug())) {
            log.warn("onDebugData handler registered but open-flow-debug is not enabled.");
            return;
        }
        typedHandlerMap.put(FlowEventType.DEBUG, handler);
    }

    // 节点进入事件
    public void onNodeEnter(FlowHandler<FlowNodeEnterVO> handler) { typedHandlerMap.put(FlowEventType.NODE_ENTER, handler); }
    public void onNodeEnterData(FlowHandler<FlowData> handler) { typedHandlerMap.put(FlowEventType.NODE_ENTER, handler); }

    // 跳转事件
    public void onJump(FlowHandler<FlowJumpVO> handler) { typedHandlerMap.put(FlowEventType.JUMP, handler); }
    public void onJumpData(FlowHandler<FlowData> handler) { typedHandlerMap.put(FlowEventType.JUMP, handler); }

    /**
     * 设置全局错误处理器
     * 当事件处理器抛出异常时，会调用此处理器
     *
     * @param errorHandler 错误处理器
     * @return FlowClient 实例（支持链式调用）
     */
    @NonNull
    public FlowClient onHandlerError(@NonNull Consumer<Throwable> errorHandler) {
        this.globalErrorHandler = errorHandler;
        return this;
    }

    // 原始数据事件
    public void onRawResponse(FlowHandler<WSResponse> handler) { typedHandlerMap.put(FlowEventType.RAW_RESPONSE, handler); }
    public void onRawRequest(FlowHandler<WSPayload> handler) { typedHandlerMap.put(FlowEventType.RAW_REQUEST, handler); }

    // 连接事件
    public void onConnected(Runnable handler) { typedHandlerMap.put(FlowEventType.CONNECTED, handler); }
    public void onDisconnected(Runnable handler) { typedHandlerMap.put(FlowEventType.DISCONNECTED, handler); }
    public void onReconnecting(FlowHandler<Integer> handler) { typedHandlerMap.put(FlowEventType.RECONNECTING, handler); }

    public void offMessage() { typedHandlerMap.remove(FlowEventType.MESSAGE); }
    public void offWaiting() { typedHandlerMap.remove(FlowEventType.WAITING); }
    public void offEnd() { typedHandlerMap.remove(FlowEventType.END); }
    public void offError() { typedHandlerMap.remove(FlowEventType.ERROR); }

    // ============================================================================
    // 状态管理
    // ============================================================================

    public FlowState getState() { return flowState.get(); }

    public String getSessionId() { return sessionId.get(); }

    /**
     * 等待 Flow 完成，返回 Mono
     */
    @NonNull
    public Mono<Void> done() {
        return completionSink.asMono();
    }

    // ============================================================================
    // 控制方法（返回 Mono）
    // ============================================================================

    /**
     * 启动 Flow，返回 Mono<String>（sessionId）
     */
    @NonNull
    public Mono<String> start(@NonNull String initialInput) {
        return start(initialInput, CybotStarConstants.DEFAULT_RESPONSE_TIMEOUT);
    }

    @NonNull
    public Mono<String> start(@NonNull String initialInput, long timeoutMillis) {
        if (sessionId.get() == null || sessionId.get().isEmpty()) {
            sessionId.set(UUID.randomUUID().toString());
        }
        return startInternal(initialInput, timeoutMillis);
    }

    @NonNull
    public Mono<String> startFrom(@NonNull String sessionId) {
        return startFrom(sessionId, CybotStarConstants.DEFAULT_RESPONSE_TIMEOUT);
    }

    @NonNull
    public Mono<String> startFrom(@NonNull String sessionId, long timeoutMillis) {
        return startFrom(sessionId, timeoutMillis, "");
    }

    @NonNull
    public Mono<String> startFrom(@NonNull String sessionId, long timeoutMillis, @NonNull String initialInput) {
        this.sessionId.set(sessionId);
        return startInternal(initialInput, timeoutMillis);
    }

    /**
     * 发送用户输入，返回 Mono<Void>
     */
    @NonNull
    public Mono<Void> send(@NonNull String input) {
        return sendInternal(input);
    }

    public void abort(@NonNull String reason) {
        flowState.set(FlowState.ABORTED);
        abortReason.set(reason);
        FlowException error = FlowException.flowError(reason, null);
        completionSink.tryEmitError(error);
        connectionManager.disconnectAll().subscribe();
    }

    public void close() {
        flowState.set(FlowState.COMPLETED);
        connectionManager.disconnectAll().subscribe();
    }

    // ============================================================================
    // 内部实现
    // ============================================================================

    @NonNull
    private Mono<String> startInternal(@NonNull String initialInput, long timeoutMillis) {
        flowState.set(FlowState.STARTING);
        historyExtracted.set(false);

        final String sid = sessionId.get();

        return connectionManager.getConnection(sid)
                // 确保连接已建立
                .flatMap(connection -> connection.ensureConnected()
                        .thenReturn(connection))
                // 订阅连接状态变化
                .doOnNext(connection -> {
                    connection.connectionStates()
                            .subscribe(this::handleConnectionStateChange);
                })
                // 订阅消息流
                .flatMap(connection -> {
                    // 启动消息处理
                    connection.messages()
                            .subscribe(
                                    this::handleMessage,
                                    error -> log.error("Message stream error", error)
                            );

                    // 发送启动请求
                    FlowConfig flowConfig = FlowConfig.builder()
                            .credentials(config.getCredentials())
                            .flow(config.getFlow())
                            .question(initialInput)
                            .build();
                    AgentConfig properties = AgentConfig.builder()
                            .credentials(config.getCredentials())
                            .build();
                    WSPayload payload = FlowPayloadBuilder.buildFlowPayload(properties, flowConfig, sid);

                    emit(FlowEventType.RAW_REQUEST, payload);

                    return connection.send(payload)
                            .doOnSuccess(v -> flowState.set(FlowState.RUNNING))
                            .thenReturn(sid);
                })
                .timeout(Duration.ofMillis(timeoutMillis))
                .onErrorMap(error -> {
                    if (error instanceof FlowException) {
                        return error;
                    }
                    return FlowException.connectionFailed("Flow startup failed: " + error.getMessage(),
                            error instanceof Exception ? (Exception) error : null);
                });
    }

    @NonNull
    private Mono<Void> sendInternal(@NonNull String input) {
        FlowState currentState = getState();
        if (currentState != FlowState.RUNNING && currentState != FlowState.WAITING) {
            FlowException error = FlowException.notRunning();
            emit(FlowEventType.ERROR, error);
            completionSink.tryEmitError(error);
            return Mono.error(error);
        }
        if (currentState != FlowState.WAITING) {
            FlowException error = FlowException.notWaiting();
            emit(FlowEventType.ERROR, error);
            completionSink.tryEmitError(error);
            return Mono.error(error);
        }

        flowState.set(FlowState.RUNNING);

        final String sid = sessionId.get();
        FlowConfig flowConfig = FlowConfig.builder()
                .credentials(config.getCredentials())
                .flow(config.getFlow())
                .question(input)
                .build();
        AgentConfig properties = AgentConfig.builder()
                .credentials(config.getCredentials())
                .build();
        WSPayload payload = FlowPayloadBuilder.buildFlowPayload(properties, flowConfig, sid);

        emit(FlowEventType.RAW_REQUEST, payload);

        return connectionManager.getConnection(sid)
                .flatMap(connection -> {
                    if (!connection.isConnected()) {
                        FlowException error = FlowException.connectionClosed();
                        emit(FlowEventType.ERROR, error);
                        completionSink.tryEmitError(error);
                        return Mono.error(error);
                    }
                    return connection.send(payload);
                });
    }

    // ============================================================================
    // 连接状态 & 消息处理
    // ============================================================================

    private void handleConnectionStateChange(@NonNull ConnectionState state) {
        switch (state) {
            case CONNECTED: emit(FlowEventType.CONNECTED); break;
            case DISCONNECTED: case CLOSED: emit(FlowEventType.DISCONNECTED); break;
            case RECONNECTING: emit(FlowEventType.RECONNECTING, 0); break;
            default: break;
        }
    }

    private void handleMessage(@NonNull WSResponse response) {
        FlowData.MessageData messageData = response.getData() instanceof Map
                ? JSON.parseObject(JSON.toJSONString(response.getData()), FlowData.MessageData.class)
                : null;
        String dataCode = messageData != null && messageData.getCode() != null ? messageData.getCode() : "";
        String topLevelCode = response.getCode();
        boolean isStartEvent = "002000".equals(dataCode) || "002000".equals(topLevelCode);

        FlowState currentState = getState();
        boolean isStarted = currentState == FlowState.RUNNING || currentState == FlowState.WAITING || currentState == FlowState.STARTING;
        if (!isStarted && !isStartEvent) return;
        if (ResponseType.isType(response.getType(), ResponseType.HEARTBEAT)) return;

        emit(FlowEventType.RAW_RESPONSE, response);

        if (topLevelCode != null && topLevelCode.startsWith("4")) {
            String errorMessage = response.getMessage() != null ? response.getMessage() : "Unknown error";
            FlowException error = new FlowException(topLevelCode, errorMessage, Map.of("response", response));
            flowState.set(FlowState.ERROR);
            emit(FlowEventType.ERROR, error);
            completionSink.tryEmitError(error);
            return;
        }

        Object dataObj = response.getData();
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = (Map<String, Object>) dataObj;
            String answer = (String) responseData.getOrDefault("answer", "");
            if (answer.contains("系统异常(")) {
                FlowException error = FlowException.fromServerMessage(answer, response);
                flowState.set(FlowState.ERROR);
                emit(FlowEventType.ERROR, error);
                completionSink.tryEmitError(error);
                return;
            }
            if (answer.contains("涉及到风险")) {
                FlowException error = FlowException.flowError("Risk control blocked: " + answer, response);
                flowState.set(FlowState.ABORTED);
                abortReason.set(answer);
                FlowErrorVO errorVO = new FlowErrorVO();
                errorVO.setErrorMessage(answer);
                errorVO.setMessage("风控拦截");
                emit(FlowEventType.ERROR, errorVO);
                completionSink.tryEmitError(error);
                return;
            }
        }
        handleFlowMessage(response);
    }

    private void handleFlowMessage(@NonNull WSResponse response) {
        if (response.getData() instanceof String) {
            String textContent = (String) response.getData();
            if (textContent != null && textContent.contains("涉及到风险")) {
                FlowException error = FlowException.flowError("Risk control blocked: " + textContent, response);
                flowState.set(FlowState.ABORTED);
                abortReason.set(textContent);
                FlowErrorVO errorVO = new FlowErrorVO();
                errorVO.setErrorMessage(textContent);
                errorVO.setMessage("风控拦截");
                emit(FlowEventType.ERROR, errorVO);
                completionSink.tryEmitError(error);
                return;
            }

            FlowData flowData = new FlowData();
            flowData.setCode(response.getCode());
            flowData.setMessage(response.getMessage());
            flowData.setType(response.getType());

            boolean isFinished = "y".equalsIgnoreCase(response.getFinish());

            Object handler = typedHandlerMap.get(FlowEventType.MESSAGE);
            if (handler instanceof MessageHandler) {
                emit(FlowEventType.MESSAGE, isFinished ? "" : textContent, isFinished);
            } else if (handler instanceof FlowHandler) {
                FlowMessageVO vo = new FlowMessageVO();
                vo.setDisplayText(isFinished ? "" : textContent);
                vo.setFinished(isFinished);
                emit(FlowEventType.MESSAGE, vo);
            }
            if (isFinished && flowState.get() == FlowState.RUNNING) {
                flowState.set(FlowState.WAITING);
            }
            return;
        }

        FlowData flowData = JSON.parseObject(JSON.toJSONString(response), FlowData.class);
        if (flowData == null) return;

        FlowData.MessageData messageData = flowData.getData();
        String code = "";
        if (messageData != null && messageData.getCode() != null) code = messageData.getCode();
        else if (flowData.getCode() != null) code = flowData.getCode();

        FlowEventType eventType = FlowEventType.fromEventCode(code);
        if (eventType != null) {
            switch (eventType) {
                case START: handleFlowStart(flowData); break;
                case END: handleFlowEnd(flowData); break;
                case NODE_ENTER: handleNodeEnter(flowData); break;
                case DEBUG: handleDebugMessage(flowData); break;
                case WAITING: handleWaiting(flowData); break;
                case JUMP: handleJump(flowData); break;
                case SUCCESS: handleSuccessResponse(flowData); break;
                case ROUND_COMPLETE: break;
                case ERROR: handleFlowError(flowData); break;
                default: break;
            }
        }
    }

    // ============================================================================
    // 事件处理方法
    // ============================================================================

    private void handleFlowStart(@NonNull FlowData flowData) {
        Object handler = typedHandlerMap.get(FlowEventType.START);
        if (handler == null) return;
        FlowStartVO vo = FlowVOExtractor.extractFlowStartVO(flowData);
        emit(FlowEventType.START, vo);
    }

    private void handleFlowEnd(@NonNull FlowData flowData) {
        flowState.set(FlowState.COMPLETED);
        String finalText = "";
        FlowData.MessageData md = flowData.getData();
        if (md != null) {
            if (md.getAnswer() != null && !md.getAnswer().isEmpty()) finalText = md.getAnswer();
            else if (md.getOutput() != null && md.getOutput().getRobotUserReplying() != null) finalText = md.getOutput().getRobotUserReplying();
        }
        Object handler = typedHandlerMap.get(FlowEventType.END);
        if (handler != null) {
            FlowEndVO vo = FlowVOExtractor.extractFlowEndVO(flowData, finalText);
            emit(FlowEventType.END, vo);
        }
        completionSink.tryEmitEmpty();
    }

    private void handleMessageEvent(@NonNull FlowData flowData) {
        Object handler = typedHandlerMap.get(FlowEventType.MESSAGE);
        if (handler == null) return;
        FlowData.MessageData md = flowData.getData();
        if (handler instanceof MessageHandler) {
            String msg = FlowUtils.extractFlowDisplayText(md);
            if (msg == null) msg = "";
            boolean isFinished = FlowUtils.isMessageFinished(md);
            emit(FlowEventType.MESSAGE, msg, isFinished);
        } else {
            emit(FlowEventType.MESSAGE, FlowVOExtractor.extractFlowMessageVO(flowData));
        }
    }

    private void handleNodeEnter(@NonNull FlowData flowData) {
        Object handler = typedHandlerMap.get(FlowEventType.NODE_ENTER);
        if (handler == null) return;
        if (flowData.getData() == null) return;
        FlowNodeEnterVO vo = FlowVOExtractor.extractFlowNodeEnterVO(flowData);
        emit(FlowEventType.NODE_ENTER, vo);
    }

    private void handleDebugMessage(@NonNull FlowData flowData) {
        FlowData.MessageData md = flowData.getData();
        if (md == null) return;
        String answer = md.getAnswer() != null ? md.getAnswer() : "";
        if (answer.contains("entity:") || answer.contains("node_id:")) return;
        Object handler = typedHandlerMap.get(FlowEventType.DEBUG);
        if (handler != null) {
            FlowDebugVO vo = FlowVOExtractor.extractFlowDebugVO(flowData, answer);
            emit(FlowEventType.DEBUG, vo);
        }
    }

    private void handleWaiting(@NonNull FlowData flowData) {
        FlowData.MessageData md = flowData.getData();
        if (md == null) return;
        extractAndEmitMessage(md, flowData, FlowEventType.WAITING.getEventCode());
        if (flowState.get() != FlowState.WAITING) {
            flowState.set(FlowState.WAITING);
            Object handler = typedHandlerMap.get(FlowEventType.WAITING);
            if (handler != null) {
                FlowWaitingVO vo = FlowVOExtractor.extractFlowWaitingVO(flowData);
                emit(FlowEventType.WAITING, vo);
            }
        }
    }

    private void handleJump(@NonNull FlowData flowData) {
        String jumpType = "unknown";
        Object handler = typedHandlerMap.get(FlowEventType.JUMP);
        if (handler != null) {
            FlowJumpVO vo = FlowVOExtractor.extractFlowJumpVO(flowData, jumpType);
            emit(FlowEventType.JUMP, vo);
        }
    }

    private void handleSuccessResponse(@NonNull FlowData flowData) {
        FlowData.MessageData md = flowData.getData();
        if (md == null) return;
        Integer nodeWaitingInput = flowData.getNodeWaitingInput();
        String displayText = FlowUtils.extractFlowDisplayText(md);
        Integer answerIndex = FlowUtils.getAnswerIndex(md);
        boolean isFinished = FlowUtils.isMessageFinished(md);

        if (displayText == null && nodeWaitingInput != null && nodeWaitingInput == 1) {
            extractAndEmitMessage(md, flowData, "000000");
        } else if (displayText != null && !(isFinished && answerIndex != null)) {
            handleMessageEvent(flowData);
            historyExtracted.set(true);
        }
        if (nodeWaitingInput != null && nodeWaitingInput == 1) {
            if (flowState.get() != FlowState.WAITING) {
                flowState.set(FlowState.WAITING);
                FlowWaitingVO vo = FlowVOExtractor.extractFlowWaitingVO(flowData);
                emit(FlowEventType.WAITING, vo);
            }
        }
    }

    private void handleFlowError(@NonNull FlowData flowData) {
        String errorMessage = flowData.getMessage() != null ? flowData.getMessage() : "Flow execution error";
        FlowException error = FlowException.flowError(errorMessage, null);
        flowState.set(FlowState.ERROR);
        Object handler = typedHandlerMap.get(FlowEventType.ERROR);
        if (handler != null) {
            FlowErrorVO vo = FlowVOExtractor.extractFlowErrorVO(flowData, error);
            emit(FlowEventType.ERROR, vo);
        }
        completionSink.tryEmitError(error);
    }

    private void extractAndEmitMessage(FlowData.MessageData messageData, FlowData flowData, String context) {
        String displayText = FlowUtils.extractFlowDisplayText(messageData);
        Integer answerIndex = FlowUtils.getAnswerIndex(messageData);
        boolean isFinished = FlowUtils.isMessageFinished(messageData);
        if (displayText != null && !(isFinished && answerIndex != null)) {
            handleMessageEvent(flowData);
            historyExtracted.set(true);
        } else {
            if (!historyExtracted.get()) {
                String historyDisplayText = extractMessageFromHistory(messageData);
                if (historyDisplayText != null) {
                    handleMessageEvent(flowData);
                    historyExtracted.set(true);
                }
            }
        }
    }

    private String extractMessageFromHistory(FlowData.MessageData messageData) {
        if (messageData == null) return null;
        List<Map<String, Object>> history = messageData.getHistory();
        if (history == null || history.isEmpty()) return null;
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> item = history.get(i);
            if (item == null) continue;
            String asking = (String) item.get("robot_user_asking");
            String replying = (String) item.get("robot_user_replying");
            if (asking != null && !asking.trim().isEmpty()) return asking;
            else if (replying != null && !replying.trim().isEmpty()) return replying;
        }
        return null;
    }

    // ============================================================================
    // 事件分发
    // ============================================================================

    private void emit(@NonNull FlowEventType event, Object... args) {
        Object handler = typedHandlerMap.get(event);
        if (handler != null) {
            try {
                invokeTypedHandler(event, handler, args);
            } catch (Exception e) {
                log.error("Error in typed {} handler", event, e);
                // 调用全局错误处理器
                if (globalErrorHandler != null) {
                    try {
                        globalErrorHandler.accept(e);
                    } catch (Exception handlerError) {
                        log.error("Error in global error handler", handlerError);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void invokeTypedHandler(@NonNull FlowEventType event, @NonNull Object handler, Object... args) {
        switch (event) {
            case MESSAGE:
                if (handler instanceof MessageHandler && args.length >= 2 && args[0] instanceof String && args[1] instanceof Boolean) {
                    ((MessageHandler) handler).handle((String) args[0], (Boolean) args[1]);
                } else if (handler instanceof FlowHandler && args.length > 0) {
                    ((FlowHandler<Object>) handler).handle(args[0]);
                }
                break;
            case WAITING:
            case END:
            case ERROR:
            case START:
            case DEBUG:
            case NODE_ENTER:
            case JUMP:
                if (handler instanceof FlowHandler && args.length > 0) {
                    ((FlowHandler<Object>) handler).handle(args[0]);
                }
                break;
            case RAW_RESPONSE:
            case RAW_REQUEST:
                if (handler instanceof FlowHandler && args.length > 0) {
                    ((FlowHandler<Object>) handler).handle(args[0]);
                }
                break;
            case CONNECTED:
            case DISCONNECTED:
                if (handler instanceof Runnable) {
                    ((Runnable) handler).run();
                } else if (handler instanceof FlowHandler) {
                    ((FlowHandler<Void>) handler).handle(null);
                }
                break;
            case RECONNECTING:
                if (handler instanceof FlowHandler && args.length > 0 && args[0] instanceof Integer) {
                    ((FlowHandler<Integer>) handler).handle((Integer) args[0]);
                }
                break;
            default: break;
        }
    }
}

