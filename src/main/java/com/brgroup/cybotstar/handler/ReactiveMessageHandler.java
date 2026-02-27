package com.brgroup.cybotstar.handler;

import com.brgroup.cybotstar.model.common.ResponseIndex;
import com.brgroup.cybotstar.model.common.ResponseType;
import com.brgroup.cybotstar.model.ws.WSResponse;
import com.brgroup.cybotstar.model.ws.WSResponseData;
import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * 响应式消息处理器
 * 使用 Flux 操作符处理消息流
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class ReactiveMessageHandler {

    /**
     * 消息事件类型
     */
    public enum MessageEventType {
        CHUNK,          // 流式 chunk
        COMPLETE,       // 完成
        ERROR,          // 错误
        REASONING,      // Reasoning
        SPECIAL         // 特殊消息（联网搜索、图片等）
    }

    /**
     * 消息事件
     */
    @Data
    @AllArgsConstructor
    public static class MessageEvent {
        private MessageEventType type;
        private String content;
        private String dialogId;
        private Throwable error;

        public static MessageEvent chunk(String content) {
            return new MessageEvent(MessageEventType.CHUNK, content, null, null);
        }

        public static MessageEvent complete(String content, String dialogId) {
            return new MessageEvent(MessageEventType.COMPLETE, content, dialogId, null);
        }

        public static MessageEvent error(Throwable error) {
            return new MessageEvent(MessageEventType.ERROR, null, null, error);
        }

        public static MessageEvent reasoning(String content) {
            return new MessageEvent(MessageEventType.REASONING, content, null, null);
        }

        public static MessageEvent special(String content) {
            return new MessageEvent(MessageEventType.SPECIAL, content, null, null);
        }
    }

    /**
     * 处理消息流，返回事件流
     */
    @NonNull
    public Flux<MessageEvent> handle(@NonNull Flux<WSResponse> messages) {
        return messages
                // 过滤心跳消息
                .filter(msg -> !ResponseType.isType(msg.getType(), ResponseType.HEARTBEAT))
                // 处理特殊索引消息
                .flatMap(this::handleSpecialIndex)
                // 检查错误
                .flatMap(this::checkError)
                // 转换为事件
                .flatMap(this::toMessageEvent);
    }

    /**
     * 提取流式内容
     */
    @NonNull
    public Flux<String> extractStreamContent(@NonNull Flux<WSResponse> messages) {
        return handle(messages)
                .filter(event -> event.getType() == MessageEventType.CHUNK)
                .map(MessageEvent::getContent)
                .filter(content -> content != null && !content.isEmpty());
    }

    /**
     * 等待完成信号
     */
    @NonNull
    public Mono<String> waitForCompletion(@NonNull Flux<WSResponse> messages) {
        return handle(messages)
                .filter(event -> event.getType() == MessageEventType.COMPLETE)
                .next()
                .map(MessageEvent::getContent);
    }

    /**
     * 处理特殊索引消息
     */
    @NonNull
    private Mono<WSResponse> handleSpecialIndex(@NonNull WSResponse response) {
        Integer respIndex = response.getIndex();
        if (respIndex == null) {
            return Mono.just(response);
        }

        Optional<ResponseIndex> indexType = ResponseIndex.fromValue(respIndex);
        if (indexType.isEmpty()) {
            return Mono.just(response);
        }

        ResponseIndex index = indexType.get();
        return switch (index) {
            case THREAD_INFO, MESSAGE_CONFIRMED -> {
                // 忽略这些消息
                log.debug("Ignoring special index message: {}", index);
                yield Mono.empty();
            }
            case ONLINE_SEARCH, IMAGE_REFERENCE, REASONING -> {
                // 这些消息需要特殊处理，但仍然传递下去
                log.debug("Received special message: {}", index);
                yield Mono.just(response);
            }
        };
    }

    /**
     * 检查错误
     */
    @NonNull
    private Mono<WSResponse> checkError(@NonNull WSResponse response) {
        String respCode = response.getCode();
        if (respCode != null && !"000000".equals(respCode)) {
            String errorMessage = response.getMessage() != null
                    ? response.getMessage()
                    : "服务器返回错误码: " + respCode;
            log.warn("Error response, code={}, message={}", respCode, errorMessage);
            return Mono.error(new RuntimeException(errorMessage));
        }
        return Mono.just(response);
    }

    /**
     * 转换为消息事件
     */
    @NonNull
    private Mono<MessageEvent> toMessageEvent(@NonNull WSResponse response) {
        String finishFlag = response.getFinish();
        String respType = response.getType();
        Integer respIndex = response.getIndex();

        // 检查是否是 Reasoning 消息
        if (respIndex != null) {
            Optional<ResponseIndex> indexType = ResponseIndex.fromValue(respIndex);
            if (indexType.isPresent() && indexType.get() == ResponseIndex.REASONING) {
                String content = extractText(response);
                return Mono.just(MessageEvent.reasoning(content));
            }
        }

        // 检查是否是特殊消息
        if (respIndex != null) {
            Optional<ResponseIndex> indexType = ResponseIndex.fromValue(respIndex);
            if (indexType.isPresent()) {
                ResponseIndex index = indexType.get();
                if (index == ResponseIndex.ONLINE_SEARCH || index == ResponseIndex.IMAGE_REFERENCE) {
                    String content = extractText(response);
                    return Mono.just(MessageEvent.special(content));
                }
            }
        }

        // 检查是否是完成消息
        boolean isFinal = "y".equals(finishFlag) || ResponseType.isType(respType, ResponseType.LLM_END);
        if (isFinal) {
            String content = extractText(response);
            String dialogId = response.getDialogId();
            return Mono.just(MessageEvent.complete(content, dialogId));
        }

        // 普通 chunk 消息
        String content = extractText(response);
        if (content != null && !content.isEmpty()) {
            return Mono.just(MessageEvent.chunk(content));
        }

        // 空消息，忽略
        return Mono.empty();
    }

    /**
     * 提取文本内容
     */
    @NonNull
    private String extractText(@NonNull WSResponse response) {
        Object data = response.getData();
        if (data == null) {
            return "";
        }

        // 情况1: data 是字符串
        if (data instanceof String) {
            return (String) data;
        }

        // 情况2: 如果是 reasoning 类型，提取 content 字段
        String respType = response.getType();
        if ("reasoning".equals(respType)) {
            try {
                String jsonStr = JSON.toJSONString(data);
                @SuppressWarnings("unchecked")
                Map<String, Object> map = JSON.parseObject(jsonStr, Map.class);
                if (map != null && map.containsKey("content")) {
                    Object content = map.get("content");
                    return content != null ? content.toString() : "";
                }
            } catch (Exception e) {
                log.debug("Failed to parse reasoning data", e);
            }
        }

        // 情况3: data 是对象且包含 answer 字段
        if (data instanceof WSResponseData responseData) {
            return responseData.getAnswer() != null ? responseData.getAnswer() : "";
        }

        // 情况4: 尝试解析为 JSON 对象
        try {
            String jsonStr = JSON.toJSONString(data);
            WSResponseData responseData = JSON.parseObject(jsonStr, WSResponseData.class);
            if (responseData != null && responseData.getAnswer() != null) {
                return responseData.getAnswer();
            }

            // 如果解析后没有 answer 字段，尝试直接获取 data 字段
            @SuppressWarnings("unchecked")
            Map<String, Object> map = JSON.parseObject(jsonStr, Map.class);
            if (map != null && map.containsKey("answer")) {
                Object answer = map.get("answer");
                return answer != null ? answer.toString() : "";
            }

            return "";
        } catch (Exception e) {
            log.debug("Failed to parse response data", e);
            return data.toString();
        }
    }
}
