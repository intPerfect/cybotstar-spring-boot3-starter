package com.brgroup.cybotstar.agent.util;

import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.agent.model.response.ChatHistoryItem;
import com.brgroup.cybotstar.agent.model.request.MessageParam;
import com.brgroup.cybotstar.agent.model.request.ExtendedSendOptions;
import com.brgroup.cybotstar.core.model.ws.WSPayload;
import com.brgroup.cybotstar.core.util.CybotStarUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * WebSocket Payload 构建器
 * 负责构建 Agent 请求的 WSPayload
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class PayloadBuilder {

    /**
     * 构造 WebSocket 发送载荷
     * 会话上下文通过保持 WebSocket 连接来实现。
     * 如果提供了 sessionId，会自动将其设置为 segment_code。
     * 支持扩展参数：message_params, chat_history, tip_message_extra, tip_message_params,
     * model_params
     *
     * @param config    客户端配置
     * @param question  用户问题
     * @param sessionId 会话ID，如果提供则自动设置为 segment_code
     * @param options   扩展选项
     * @return WebSocket 发送载荷
     */
    @SuppressWarnings("deprecation") // 需要继续支持废弃字段以保持兼容性
    @NonNull
    public static WSPayload buildPayload(@NonNull AgentConfig config, @NonNull String question, @Nullable String sessionId,
            @Nullable ExtendedSendOptions options) {
        WSPayload payload = new WSPayload();
        payload.setCybertronRobotKey(config.getCredentials().getRobotKey());
        payload.setCybertronRobotToken(config.getCredentials().getRobotToken());
        payload.setUsername(config.getCredentials().getUsername());

        // 检查是否设置了 messageParams
        List<MessageParam> messageParams = options != null ? options.getMessageParams() : null;

        // question 字段必须设置（服务端要求）
        // 如果设置了 messageParams，尝试从最后一个 user 消息中提取 question
        // 如果没有 user 消息，则使用 prompt() 传入的值
        String finalQuestion = question;
        if (messageParams != null && !messageParams.isEmpty()) {
            // 从后往前查找最后一个 user 消息
            for (int i = messageParams.size() - 1; i >= 0; i--) {
                MessageParam param = messageParams.get(i);
                if ("user".equals(param.getRole()) && StringUtils.isNotBlank(param.getContent())) {
                    finalQuestion = param.getContent();
                    break;
                }
            }
        }
        payload.setQuestion(finalQuestion);

        // 如果提供了 sessionId，自动设置为 segment_code
        if (StringUtils.isNotBlank(sessionId)) {
            payload.setSegmentCode(sessionId);
        }

        if (options != null) {
            if (StringUtils.isNotBlank(options.getExtraHeader())) {
                payload.setExtraHeader(options.getExtraHeader());
            }
            if (StringUtils.isNotBlank(options.getExtraBody())) {
                payload.setExtraBody(options.getExtraBody());
            }

            // message_params 优先级最高，与其他两者互斥
            // tip_message_extra 和 tip_message_params 可以同时存在
            if (messageParams != null && !messageParams.isEmpty()) {
                // messageParams 已设置，设置到 payload
                payload.setMessageParams(messageParams);

                // 检测是否同时使用了废弃字段，输出警告
                if (StringUtils.isNotBlank(options.getTipMessageExtra())
                        || (options.getTipMessageParams() != null && !options.getTipMessageParams().isEmpty())
                        || (options.getChatHistory() != null && !options.getChatHistory().isEmpty())) {
                    log.warn("Detected simultaneous use of messageParams and deprecated fields (tipMessageExtra/tipMessageParams/chatHistory). " +
                            "messageParams has higher priority, deprecated fields will be ignored. Please remove deprecated fields and use messageParams uniformly.");
                }
            } else {
                // 没有 messageParams，处理 tip_message_extra 和 tip_message_params
                // tip_message_extra 和 tip_message_params 可以同时传递
                // 当两者同时存在时，将 tip_message_params 中的变量替换到 tip_message_extra 中
                String tipMessageExtra = options.getTipMessageExtra();
                if (StringUtils.isNotBlank(tipMessageExtra)) {
                    // 输出废弃警告
                    log.warn("tipMessageExtra field is deprecated, please use messageParams instead. " +
                            "You can set role settings via MessageParam.system(content).");

                    Map<String, String> tipMessageParams = options.getTipMessageParams();
                    if (tipMessageParams != null && !tipMessageParams.isEmpty()) {
                        // 同时存在时，进行变量替换
                        payload.setTipMessageExtra(
                                CybotStarUtils.replaceTemplateVariables(tipMessageExtra, tipMessageParams));
                    } else {
                        payload.setTipMessageExtra(tipMessageExtra);
                    }
                }
                // tip_message_params 只在单独传递时设置到 payload
                // 如果同时存在 tip_message_extra，则只设置替换后的 tip_message_extra
                Map<String, String> tipMessageParams = options.getTipMessageParams();
                if (tipMessageParams != null && !tipMessageParams.isEmpty() && StringUtils.isBlank(tipMessageExtra)) {
                    // 输出废弃警告
                    log.warn("tipMessageParams field is deprecated, please use messageParams instead. " +
                            "You can set role settings via MessageParam.system(content) and use variable substitution in content.");
                    payload.setTipMessageParams(tipMessageParams);
                }
            }

            // chat_history 和 message_params 互斥
            List<ChatHistoryItem> chatHistory = options.getChatHistory();
            if (chatHistory != null && !chatHistory.isEmpty() && (messageParams == null || messageParams.isEmpty())) {
                // 输出废弃警告
                log.warn("chatHistory field is deprecated, please use messageParams instead. " +
                        "You can set conversation history via MessageParam.assistant(content) and MessageParam.user(content).");
                payload.setChatHistory(chatHistory);
            }

            if (options.getModelOptions() != null) {
                payload.setModelOptions(options.getModelOptions());
            }
        }

        return payload;
    }

    /**
     * 构造心跳请求载荷
     *
     * @return 心跳请求载荷 JSON 字符串
     */
    @NonNull
    public static String buildHeartbeatPayload() {
        return "{\"type\":\"heartbeat\",\"data\":\"ping\"}";
    }
}
