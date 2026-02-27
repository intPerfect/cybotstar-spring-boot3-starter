package com.brgroup.cybotstar.reactive;

import com.brgroup.cybotstar.config.AgentConfig;
import com.brgroup.cybotstar.config.FlowConfig;
import com.brgroup.cybotstar.connection.SingleConnectionManager;
import com.brgroup.cybotstar.connection.WebSocketConnection;
import com.brgroup.cybotstar.flow.model.FlowData;
import com.brgroup.cybotstar.flow.model.FlowEventType;
import com.brgroup.cybotstar.flow.model.FlowState;
import com.brgroup.cybotstar.flow.model.handler.*;
import com.brgroup.cybotstar.flow.model.vo.*;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reactive Flow 运行时引擎
 * 事件订阅保持回调风格，控制方法返回 Mono
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class ReactiveFlowClient {

    @Getter
    private final FlowConfig config;
    private FlowState flowState = FlowState.IDLE;
    private final SingleConnectionManager connectionManager;
    private final GenericErrorHandler errorHandler;
    private final Map<FlowEventType, Object> typedHandlerMap = new ConcurrentHashMap<>();
    private final Map<FlowEventType, Object> typedHandlerWithDataMap = new ConcurrentHashMap<>();
    private CompletableFuture<Void> completionFuture;
    private String abortReason;
    private WebSocketConnection.WSMessageHandler messageHandler;
    @Getter
    private String sessionId;
    private boolean historyExtracted = false;
    private CompletableFuture<Void> connectionFuture;

    public ReactiveFlowClient(FlowConfig config) {
        this.config = config;
        AgentConfig properties = AgentConfig.builder()
                .credentials(config.getCredentials())
                .websocket(config.getWebsocket())
                .build();
        this.connectionManager = new SingleConnectionManager(properties);
        this.errorHandler = new GenericErrorHandler();
        this.connectionManager.registerStateChangeCallback((sid, state) -> handleConnectionStateChange(state));
    }

    // ============================================================================
    // 事件订阅（与 FlowClient 完全一致）
    // ============================================================================

    public void onMessage(MessageHandler handler) { typedHandlerMap.put(FlowEventType.MESSAGE, handler); }
    public void onMessage(MessageHandlerVO handler) { typedHandlerMap.put(FlowEventType.MESSAGE, handler); }
    public void onMessage(MessageHandlerWithData handler) { typedHandlerMap.put(FlowEventType.MESSAGE, handler); }
    public void onWaiting(WaitingHandlerVO handler) { typedHandlerMap.put(FlowEventType.WAITING, handler); }
    public void onWaiting(WaitingHandler handler) { typedHandlerMap.put(FlowEventType.WAITING, handler); }
    public void onEnd(EndHandlerVO handler) { typedHandlerMap.put(FlowEventType.END, handler); }
    public void onEnd(EndHandler handler) { typedHandlerMap.put(FlowEventType.END, handler); }
    public void onError(ErrorHandlerVO handler) { typedHandlerMap.put(FlowEventType.ERROR, handler); }
    public void onError(ErrorHandler handler) { typedHandlerMap.put(FlowEventType.ERROR, handler); }
    public void onStart(StartHandler handler) { typedHandlerMap.put(FlowEventType.START, handler); }
    public void onStart(StartHandlerVO handler) { typedHandlerMap.put(FlowEventType.START, handler); }

    public void onDebug(DebugHandlerVO handler) {
        if (config.getFlow() == null || !Boolean.TRUE.equals(config.getFlow().getOpenFlowDebug())) {
            log.warn("onDebug handler registered but open-flow-debug is not enabled.");
            return;
        }
        typedHandlerMap.put(FlowEventType.DEBUG, handler);
    }

    public void onDebug(DebugHandler handler) {
        if (config.getFlow() == null || !Boolean.TRUE.equals(config.getFlow().getOpenFlowDebug())) {
            log.warn("onDebug handler registered but open-flow-debug is not enabled.");
            return;
        }
        typedHandlerMap.put(FlowEventType.DEBUG, handler);
    }

    public void onNodeEnter(NodeEnterHandler handler) { typedHandlerMap.put(FlowEventType.NODE_ENTER, handler); }
    public void onNodeEnter(NodeEnterHandlerVO handler) { typedHandlerMap.put(FlowEventType.NODE_ENTER, handler); }
    public void onJump(JumpHandlerVO handler) { typedHandlerMap.put(FlowEventType.JUMP, handler); }
    public void onJump(JumpHandler handler) { typedHandlerMap.put(FlowEventType.JUMP, handler); }
    public void onRawResponse(RawResponseHandler handler) { typedHandlerMap.put(FlowEventType.RAW_RESPONSE, handler); }
    public void onRawRequest(RawRequestHandler handler) { typedHandlerMap.put(FlowEventType.RAW_REQUEST, handler); }
    public void onConnected(ConnectedHandler handler) { typedHandlerMap.put(FlowEventType.CONNECTED, handler); }
    public void onDisconnected(DisconnectedHandler handler) { typedHandlerMap.put(FlowEventType.DISCONNECTED, handler); }
    public void onReconnecting(ReconnectingHandler handler) { typedHandlerMap.put(FlowEventType.RECONNECTING, handler); }

    public void offMessage() { typedHandlerMap.remove(FlowEventType.MESSAGE); }
    public void offWaiting() { typedHandlerMap.remove(FlowEventType.WAITING); }
    public void offEnd() { typedHandlerMap.remove(FlowEventType.END); }
    public void offError() { typedHandlerMap.remove(FlowEventType.ERROR); }

    // ============================================================================
    // 状态管理
    // ============================================================================

    public FlowState getState() { return flowState; }

    /**
     * 等待 Flow 完成，返回 Mono
     */
    public Mono<Void> done() {
        if (completionFuture == null || completionFuture.isDone()) {
            completionFuture = new CompletableFuture<>();
        }
        final CompletableFuture<Void> cf = completionFuture;
        return Mono.fromFuture(() -> cf);
    }

    // ============================================================================
    // 控制方法（返回 Mono）
    // ============================================================================

    /**
     * 启动 Flow，返回 Mono&lt;String&gt;（sessionId）
     */
    public Mono<String> start(String initialInput) {
        return start(initialInput, Constants.DEFAULT_RESPONSE_TIMEOUT);
    }

    public Mono<String> start(String initialInput, long timeoutMillis) {
        if (this.sessionId == null || this.sessionId.isEmpty()) {
            this.sessionId = UUID.randomUUID().toString();
        }
        return Mono.<String>create(sink -> {
            try {
                String result = startInternal(initialInput, timeoutMillis);
                sink.success(result);
            } catch (Exception e) {
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> startFrom(String sessionId) {
        return startFrom(sessionId, Constants.DEFAULT_RESPONSE_TIMEOUT);
    }

    public Mono<String> startFrom(String sessionId, long timeoutMillis) {
        return startFrom(sessionId, timeoutMillis, "");
    }

    public Mono<String> startFrom(String sessionId, long timeoutMillis, String initialInput) {
        this.sessionId = sessionId;
        return Mono.<String>create(sink -> {
            try {
                String result = startInternal(initialInput != null ? initialInput : "", timeoutMillis);
                sink.success(result);
            } catch (Exception e) {
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 发送用户输入，返回 Mono&lt;Void&gt;
     */
    public Mono<Void> send(String input) {
        return Mono.<Void>create(sink -> {
            try {
                sendInternal(input);
                sink.success();
            } catch (Exception e) {
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public void abort(String reason) {
        flowState = FlowState.ABORTED;
        abortReason = reason;
        FlowException error = FlowException.flowError(reason != null ? reason : "Flow 被中止", null);
        if (completionFuture != null && !completionFuture.isDone()) {
            completionFuture.completeExceptionally(error);
        }
        connectionManager.disconnect();
    }

    public void close() {
        flowState = FlowState.COMPLETED;
        connectionManager.disconnect();
    }

    // ============================================================================
    // 内部实现（复用 FlowClient 逻辑）
    // ============================================================================

    private String startInternal(String initialInput, long timeoutMillis) {
        final String finalInitialInput = initialInput != null ? initialInput : "";
        flowState = FlowState.STARTING;
        historyExtracted = false;

        WebSocketConnection connection = connectionManager.getConnection();
        if (messageHandler != null) {
            connection.removeMessageHandler(messageHandler);
        }
        messageHandler = response -> {
            try { handleMessage(response); }
            catch (Exception e) { errorHandler.handle(e, GenericErrorHandler.withContext(Map.of("method", "start.onMessage"))); }
        };
        connection.onMessage(messageHandler);

        if (completionFuture == null || completionFuture.isDone()) {
            completionFuture = new CompletableFuture<>();
        }
        connectionFuture = new CompletableFuture<>();

        connectionManager.connect().thenRun(() -> {
            flowState = FlowState.RUNNING;
            FlowConfig flowConfig = FlowConfig.builder()
                    .credentials(config.getCredentials())
                    .flow(config.getFlow())
                    .question(finalInitialInput)
                    .build();
            AgentConfig properties = AgentConfig.builder()
                    .credentials(config.getCredentials())
                    .build();
            WSPayload payload = FlowPayloadBuilder.buildFlowPayload(properties, flowConfig, this.sessionId);
            emit(FlowEventType.RAW_REQUEST, payload);
            connection.send(payload);
            connectionFuture.complete(null);
        }).exceptionally(error -> {
            log.error("Flow startup failed", error);
            FlowException flowError = error instanceof FlowException
                    ? (FlowException) error
                    : FlowException.connectionFailed(error.getMessage(), error instanceof Exception ? error : null);
            if (completionFuture != null && !completionFuture.isDone()) completionFuture.completeExceptionally(flowError);
            if (connectionFuture != null && !connectionFuture.isDone()) connectionFuture.completeExceptionally(flowError);
            return null;
        });

        try {
            connectionFuture.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                    .exceptionally(throwable -> {
                        if (throwable instanceof TimeoutException) throw FlowException.connectionTimeout(timeoutMillis);
                        if (throwable instanceof FlowException) throw (FlowException) throwable;
                        if (throwable instanceof RuntimeException) throw (RuntimeException) throwable;
                        throw new RuntimeException(throwable);
                    }).join();
        } catch (Exception e) {
            if (e instanceof FlowException) throw (FlowException) e;
            if (e.getCause() instanceof FlowException) throw (FlowException) e.getCause();
            if (e.getCause() instanceof TimeoutException) throw FlowException.connectionTimeout(timeoutMillis);
            throw FlowException.connectionFailed("Connection establishment failed: " + e.getMessage(), e);
        }
        return this.sessionId;
    }

    private void sendInternal(String input) {
        FlowState currentState = getState();
        if (currentState != FlowState.RUNNING && currentState != FlowState.WAITING) {
            FlowException error = FlowException.notRunning();
            emit(FlowEventType.ERROR, error);
            errorHandler.handle(error, GenericErrorHandler.withContext(Map.of("method", "send", "reason", "notRunning")));
            if (completionFuture != null && !completionFuture.isDone()) completionFuture.completeExceptionally(error);
            throw error;
        }
        if (currentState != FlowState.WAITING) {
            FlowException error = FlowException.notWaiting();
            emit(FlowEventType.ERROR, error);
            errorHandler.handle(error, GenericErrorHandler.withContext(Map.of("method", "send", "reason", "notWaiting")));
            if (completionFuture != null && !completionFuture.isDone()) completionFuture.completeExceptionally(error);
            throw error;
        }
        flowState = FlowState.RUNNING;
        FlowConfig flowConfig = FlowConfig.builder()
                .credentials(config.getCredentials())
                .flow(config.getFlow())
                .question(input)
                .build();
        AgentConfig properties = AgentConfig.builder()
                .credentials(config.getCredentials())
                .build();
        WSPayload payload = FlowPayloadBuilder.buildFlowPayload(properties, flowConfig, this.sessionId);
        emit(FlowEventType.RAW_REQUEST, payload);
        WebSocketConnection connection = connectionManager.get();
        if (connection != null && connection.isConnected()) {
            connection.send(payload);
        } else {
            FlowException error = FlowException.connectionClosed();
            emit(FlowEventType.ERROR, error);
            errorHandler.handle(error, GenericErrorHandler.withContext(Map.of("method", "send", "reason", "connectionClosed")));
            if (completionFuture != null && !completionFuture.isDone()) completionFuture.completeExceptionally(error);
            throw error;
        }
    }

    // ============================================================================
    // 连接状态 & 消息处理（复用 FlowClient 逻辑）
    // ============================================================================

    private void handleConnectionStateChange(ConnectionState state) {
        switch (state) {
            case CONNECTED: emit(FlowEventType.CONNECTED); break;
            case DISCONNECTED: case CLOSED: emit(FlowEventType.DISCONNECTED); break;
            case RECONNECTING: emit(FlowEventType.RECONNECTING, 0); break;
            default: break;
        }
    }

    private void handleMessage(WSResponse response) {
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
            flowState = FlowState.ERROR;
            emit(FlowEventType.ERROR, error);
            errorHandler.handle(error, GenericErrorHandler.withContext(Map.of("method", "handleMessage", "topLevelCode", topLevelCode)));
            if (completionFuture != null && !completionFuture.isDone()) completionFuture.completeExceptionally(error);
            return;
        }

        Object dataObj = response.getData();
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = (Map<String, Object>) dataObj;
            String answer = (String) responseData.getOrDefault("answer", "");
            if (answer.contains("系统异常(")) {
                FlowException error = FlowException.fromServerMessage(answer, response);
                flowState = FlowState.ERROR;
                emit(FlowEventType.ERROR, error);
                errorHandler.handle(error, GenericErrorHandler.withContext(Map.of("method", "handleMessage", "hasSystemError", true)));
                if (completionFuture != null && !completionFuture.isDone()) completionFuture.completeExceptionally(error);
                return;
            }
            if (answer.contains("涉及到风险")) {
                FlowException error = FlowException.flowError("Risk control blocked: " + answer, response);
                flowState = FlowState.ABORTED;
                abortReason = answer;
                emit(FlowEventType.ERROR, error);
                errorHandler.handle(error, GenericErrorHandler.withContext(Map.of("method", "handleMessage", "riskBlocked", true)));
                if (completionFuture != null && !completionFuture.isDone()) completionFuture.completeExceptionally(error);
                return;
            }
        }
        handleFlowMessage(response);
    }

    private void handleFlowMessage(WSResponse response) {
        // 当 data 为纯字符串时（如风控拦截），无法解析为 FlowData.MessageData
        if (response.getData() instanceof String) {
            String textContent = (String) response.getData();
            if (textContent != null && textContent.contains("涉及到风险")) {
                FlowException error = FlowException.flowError("Risk control blocked: " + textContent, response);
                flowState = FlowState.ABORTED;
                abortReason = textContent;
                emit(FlowEventType.ERROR, error);
                errorHandler.handle(error, GenericErrorHandler.withContext(Map.of("method", "handleFlowMessage", "riskBlocked", true)));
                if (completionFuture != null && !completionFuture.isDone()) completionFuture.completeExceptionally(error);
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
            } else if (handler instanceof MessageHandlerVO) {
                FlowMessageVO vo = new FlowMessageVO();
                vo.setDisplayText(isFinished ? "" : textContent);
                vo.setFinished(isFinished);
                emit(FlowEventType.MESSAGE, vo);
            }
            if (isFinished && flowState == FlowState.RUNNING) {
                flowState = FlowState.WAITING;
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
    // 事件处理方法（复用 FlowClient 逻辑）
    // ============================================================================

    private void handleFlowStart(FlowData flowData) {
        Object handler = typedHandlerMap.get(FlowEventType.START);
        if (handler == null) return;
        if (handler instanceof StartHandlerVO) {
            emit(FlowEventType.START, FlowVOExtractor.extractFlowStartVO(flowData));
        } else if (handler instanceof StartHandler) {
            emit(FlowEventType.START, flowData);
        }
    }

    private void handleFlowEnd(FlowData flowData) {
        flowState = FlowState.COMPLETED;
        String finalText = "";
        FlowData.MessageData md = flowData.getData();
        if (md != null) {
            if (md.getAnswer() != null && !md.getAnswer().isEmpty()) finalText = md.getAnswer();
            else if (md.getOutput() != null && md.getOutput().getRobotUserReplying() != null) finalText = md.getOutput().getRobotUserReplying();
        }
        Object handler = typedHandlerMap.get(FlowEventType.END);
        if (handler != null) {
            if (handler instanceof EndHandlerVO) emit(FlowEventType.END, FlowVOExtractor.extractFlowEndVO(flowData, finalText));
            else if (handler instanceof EndHandler) emit(FlowEventType.END, flowData);
        }
        if (completionFuture != null && !completionFuture.isDone()) completionFuture.complete(null);
    }

    private void handleMessageEvent(FlowData flowData) {
        Object handler = typedHandlerMap.get(FlowEventType.MESSAGE);
        if (handler == null) return;
        FlowData.MessageData md = flowData.getData();
        if (handler instanceof MessageHandler) {
            String msg = FlowUtils.extractFlowDisplayText(md);
            if (msg == null) msg = "";
            boolean isFinished = FlowUtils.isMessageFinished(md);
            emit(FlowEventType.MESSAGE, msg, isFinished);
        } else if (handler instanceof MessageHandlerVO) {
            emit(FlowEventType.MESSAGE, FlowVOExtractor.extractFlowMessageVO(flowData));
        } else if (handler instanceof MessageHandlerWithData) {
            emit(FlowEventType.MESSAGE, flowData);
        }
    }

    private void handleNodeEnter(FlowData flowData) {
        Object handler = typedHandlerMap.get(FlowEventType.NODE_ENTER);
        if (handler == null) return;
        if (flowData.getData() == null) return;
        if (handler instanceof NodeEnterHandlerVO) emit(FlowEventType.NODE_ENTER, FlowVOExtractor.extractFlowNodeEnterVO(flowData));
        else if (handler instanceof NodeEnterHandler) emit(FlowEventType.NODE_ENTER, flowData);
    }

    private void handleDebugMessage(FlowData flowData) {
        FlowData.MessageData md = flowData.getData();
        if (md == null) return;
        String answer = md.getAnswer() != null ? md.getAnswer() : "";
        if (answer.contains("entity:") || answer.contains("node_id:")) return;
        Object handler = typedHandlerMap.get(FlowEventType.DEBUG);
        if (handler != null) {
            if (handler instanceof DebugHandlerVO) emit(FlowEventType.DEBUG, FlowVOExtractor.extractFlowDebugVO(flowData, answer));
            else if (handler instanceof DebugHandler) emit(FlowEventType.DEBUG, flowData);
        }
    }

    private void handleWaiting(FlowData flowData) {
        FlowData.MessageData md = flowData.getData();
        if (md == null) return;
        extractAndEmitMessage(md, flowData, FlowEventType.WAITING.getEventCode());
        if (flowState != FlowState.WAITING) {
            flowState = FlowState.WAITING;
            Object handler = typedHandlerMap.get(FlowEventType.WAITING);
            if (handler != null) {
                if (handler instanceof WaitingHandlerVO) emit(FlowEventType.WAITING, FlowVOExtractor.extractFlowWaitingVO(flowData));
                else if (handler instanceof WaitingHandler) emit(FlowEventType.WAITING, flowData);
            }
        }
    }

    private void handleJump(FlowData flowData) {
        String jumpType = "unknown";
        Object handler = typedHandlerMap.get(FlowEventType.JUMP);
        if (handler != null) {
            if (handler instanceof JumpHandlerVO) emit(FlowEventType.JUMP, FlowVOExtractor.extractFlowJumpVO(flowData, jumpType));
            else if (handler instanceof JumpHandler) emit(FlowEventType.JUMP, flowData);
        }
    }

    private void handleSuccessResponse(FlowData flowData) {
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
            historyExtracted = true;
        }
        if (nodeWaitingInput != null && nodeWaitingInput == 1) {
            if (flowState != FlowState.WAITING) {
                flowState = FlowState.WAITING;
                emit(FlowEventType.WAITING, flowData);
            }
        }
    }

    private void handleFlowError(FlowData flowData) {
        String errorMessage = flowData.getMessage() != null ? flowData.getMessage() : "Flow execution error";
        FlowException error = FlowException.flowError(errorMessage, null);
        flowState = FlowState.ERROR;
        Object handler = typedHandlerMap.get(FlowEventType.ERROR);
        if (handler != null) {
            if (handler instanceof ErrorHandlerVO) emit(FlowEventType.ERROR, FlowVOExtractor.extractFlowErrorVO(flowData, error));
            else if (handler instanceof ErrorHandler) emit(FlowEventType.ERROR, flowData);
        }
        errorHandler.handle(error, GenericErrorHandler.withContext(Map.of("method", "handleFlowError")));
        if (completionFuture != null && !completionFuture.isDone()) completionFuture.completeExceptionally(error);
    }

    private void extractAndEmitMessage(FlowData.MessageData messageData, FlowData flowData, String context) {
        String displayText = FlowUtils.extractFlowDisplayText(messageData);
        Integer answerIndex = FlowUtils.getAnswerIndex(messageData);
        boolean isFinished = FlowUtils.isMessageFinished(messageData);
        if (displayText != null && !(isFinished && answerIndex != null)) {
            handleMessageEvent(flowData);
            historyExtracted = true;
        } else {
            if (!historyExtracted) {
                String historyDisplayText = extractMessageFromHistory(messageData);
                if (historyDisplayText != null) {
                    handleMessageEvent(flowData);
                    historyExtracted = true;
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
    // 事件分发（复用 FlowClient 逻辑）
    // ============================================================================

    private void emit(FlowEventType event, Object... args) {
        Object handler = typedHandlerMap.get(event);
        if (handler != null) {
            try { invokeTypedHandler(event, handler, args); }
            catch (Exception e) { log.error("Error in typed {} handler", event, e); }
        }
    }

    @SuppressWarnings("unchecked")
    private void invokeTypedHandler(FlowEventType event, Object handler, Object... args) {
        switch (event) {
            case MESSAGE:
                if (handler instanceof MessageHandler && args.length >= 2 && args[0] instanceof String && args[1] instanceof Boolean)
                    ((MessageHandler) handler).handle((String) args[0], (Boolean) args[1]);
                else if (handler instanceof MessageHandlerVO && args.length > 0 && args[0] instanceof FlowMessageVO)
                    ((MessageHandlerVO) handler).handle((FlowMessageVO) args[0]);
                else if (handler instanceof MessageHandlerWithData && args.length > 0 && args[0] instanceof FlowData)
                    ((MessageHandlerWithData) handler).handle((FlowData) args[0]);
                break;
            case WAITING:
                if (handler instanceof WaitingHandlerVO && args.length > 0 && args[0] instanceof FlowWaitingVO)
                    ((WaitingHandlerVO) handler).handle((FlowWaitingVO) args[0]);
                else if (handler instanceof WaitingHandler && args.length > 0 && args[0] instanceof FlowData)
                    ((WaitingHandler) handler).handle((FlowData) args[0]);
                break;
            case END:
                if (handler instanceof EndHandlerVO && args.length > 0 && args[0] instanceof FlowEndVO)
                    ((EndHandlerVO) handler).handle((FlowEndVO) args[0]);
                else if (handler instanceof EndHandler && args.length > 0 && args[0] instanceof FlowData)
                    ((EndHandler) handler).handle((FlowData) args[0]);
                break;
            case ERROR:
                if (handler instanceof ErrorHandlerVO && args.length > 0 && args[0] instanceof FlowErrorVO)
                    ((ErrorHandlerVO) handler).handle((FlowErrorVO) args[0]);
                else if (handler instanceof ErrorHandler && args.length > 0 && args[0] instanceof FlowData)
                    ((ErrorHandler) handler).handle((FlowData) args[0]);
                break;
            case START:
                if (handler instanceof StartHandlerVO && args.length > 0 && args[0] instanceof FlowStartVO)
                    ((StartHandlerVO) handler).handle((FlowStartVO) args[0]);
                else if (handler instanceof StartHandler && args.length > 0 && args[0] instanceof FlowData)
                    ((StartHandler) handler).handle((FlowData) args[0]);
                break;
            case DEBUG:
                if (handler instanceof DebugHandlerVO && args.length > 0 && args[0] instanceof FlowDebugVO)
                    ((DebugHandlerVO) handler).handle((FlowDebugVO) args[0]);
                else if (handler instanceof DebugHandler && args.length > 0 && args[0] instanceof FlowData)
                    ((DebugHandler) handler).handle((FlowData) args[0]);
                break;
            case NODE_ENTER:
                if (handler instanceof NodeEnterHandlerVO && args.length > 0 && args[0] instanceof FlowNodeEnterVO)
                    ((NodeEnterHandlerVO) handler).handle((FlowNodeEnterVO) args[0]);
                else if (handler instanceof NodeEnterHandler && args.length > 0 && args[0] instanceof FlowData)
                    ((NodeEnterHandler) handler).handle((FlowData) args[0]);
                break;
            case JUMP:
                if (handler instanceof JumpHandlerVO && args.length > 0 && args[0] instanceof FlowJumpVO)
                    ((JumpHandlerVO) handler).handle((FlowJumpVO) args[0]);
                else if (handler instanceof JumpHandler && args.length > 0 && args[0] instanceof FlowData)
                    ((JumpHandler) handler).handle((FlowData) args[0]);
                break;
            case RAW_RESPONSE:
                if (handler instanceof RawResponseHandler && args.length > 0 && args[0] instanceof WSResponse)
                    ((RawResponseHandler) handler).handle((WSResponse) args[0]);
                break;
            case RAW_REQUEST:
                if (handler instanceof RawRequestHandler && args.length > 0 && args[0] instanceof WSPayload)
                    ((RawRequestHandler) handler).handle((WSPayload) args[0]);
                break;
            case CONNECTED:
                if (handler instanceof ConnectedHandler) ((ConnectedHandler) handler).handle();
                break;
            case DISCONNECTED:
                if (handler instanceof DisconnectedHandler) ((DisconnectedHandler) handler).handle();
                break;
            case RECONNECTING:
                if (handler instanceof ReconnectingHandler && args.length > 0 && args[0] instanceof Integer)
                    ((ReconnectingHandler) handler).handle((Integer) args[0]);
                break;
            default: break;
        }
    }
}
