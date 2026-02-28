package com.brgroup.cybotstar.agent.internal;

import com.brgroup.cybotstar.agent.model.ExtendedSendOptions;
import com.brgroup.cybotstar.agent.model.ModelOptions;
import com.brgroup.cybotstar.agent.model.MessageParam;
import com.brgroup.cybotstar.util.CybotStarUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 请求构建器
 * 负责管理链式调用状态和构建请求配置
 *
 * @author zhiyuan.xi
 */
public class RequestBuilder {
    @Nullable
    private String requestQuestion;
    @Nullable
    private ExtendedSendOptions requestOptions;
    @Nullable
    private String requestSessionId;
    @Nullable
    private Duration requestTimeout;  // 请求级超时时间

    /**
     * 设置用户问题
     */
    @NonNull
    public RequestBuilder prompt(@NonNull String question) {
        this.requestQuestion = question;
        return this;
    }

    /**
     * 设置模型参数（链式调用）
     */
    @NonNull
    public RequestBuilder option(@NonNull ModelOptions modelOptions) {
        if (this.requestOptions == null) {
            this.requestOptions = new ExtendedSendOptions();
        }
        this.requestOptions.setModelOptions(modelOptions);
        return this;
    }

    /**
     * 设置消息参数（内部方法）
     */
    public void setMessageParams(@NonNull List<MessageParam> messageParams) {
        if (this.requestOptions == null) {
            this.requestOptions = new ExtendedSendOptions();
        }
        this.requestOptions.setMessageParams(messageParams);
    }

    /**
     * 设置会话ID
     */
    @NonNull
    public RequestBuilder session(@NonNull String sessionId) {
        this.requestSessionId = sessionId;
        return this;
    }

    /**
     * 设置请求超时时间
     */
    @NonNull
    public RequestBuilder timeout(@NonNull Duration timeout) {
        this.requestTimeout = timeout;
        return this;
    }

    /**
     * 设置请求超时时间（毫秒）
     */
    @NonNull
    public RequestBuilder timeout(long timeoutMillis) {
        this.requestTimeout = Duration.ofMillis(timeoutMillis);
        return this;
    }

    /**
     * 构建请求配置
     */
    @NonNull
    public RequestConfig buildRequestConfig(@NonNull String defaultSessionId) {
        if (requestQuestion == null || requestQuestion.isEmpty()) {
            throw new IllegalArgumentException("必须调用 prompt() 设置问题");
        }

        // 如果设置了 messageParams，检查是否需要将当前问题添加到 messages 中
        // 参考 HistoryExample：如果用户指定了 .messages() 用于传递历史会话，
        // 那么也应该把这一次的 question 附带到 user() 消息中去
        if (requestOptions != null && requestOptions.getMessageParams() != null
                && !requestOptions.getMessageParams().isEmpty()) {
            List<MessageParam> messageParams = requestOptions.getMessageParams();

            // 检查最后一个消息是否是 user 类型
            MessageParam lastMessage = messageParams.get(messageParams.size() - 1);
            boolean needAppendQuestion = false;

            if (!"user".equals(lastMessage.getRole())) {
                // 最后一个消息不是 user 类型，需要添加当前问题
                needAppendQuestion = true;
            } else {
                // 最后一个消息是 user 类型，检查内容是否与当前问题一致
                String lastUserContent = lastMessage.getContent();
                if (StringUtils.isBlank(lastUserContent) || !requestQuestion.equals(lastUserContent)) {
                    // 内容不一致或为空，需要添加当前问题
                    needAppendQuestion = true;
                }
            }

            if (needAppendQuestion) {
                // 创建新的消息列表，添加当前问题
                List<MessageParam> newMessageParams = new ArrayList<>(messageParams);
                newMessageParams.add(MessageParam.user(requestQuestion));
                requestOptions.setMessageParams(newMessageParams);
            }
        }

        return new RequestConfig(
                requestQuestion,
                requestSessionId != null ? requestSessionId : defaultSessionId,
                requestOptions,
                requestTimeout);
    }

    /**
     * 重置链式调用状态
     */
    public void reset() {
        requestQuestion = null;
        requestOptions = null;
        requestSessionId = null;
        requestTimeout = null;
    }

    /**
     * 合并选项，只合并非null字段
     */
    @NonNull
    private ExtendedSendOptions mergeOptionsNonNull(@Nullable ExtendedSendOptions base, @Nullable ExtendedSendOptions override) {
        if (override == null) {
            return base;
        }
        ExtendedSendOptions merged = CybotStarUtils.mergeOptions(base, new ExtendedSendOptions());
        if (override.getExtraHeader() != null) {
            merged.setExtraHeader(override.getExtraHeader());
        }
        if (override.getExtraBody() != null) {
            merged.setExtraBody(override.getExtraBody());
        }
        if (override.getMessageParams() != null) {
            merged.setMessageParams(override.getMessageParams());
        }
        if (override.getChatHistory() != null) {
            merged.setChatHistory(override.getChatHistory());
        }
        if (override.getTipMessageExtra() != null) {
            merged.setTipMessageExtra(override.getTipMessageExtra());
        }
        if (override.getTipMessageParams() != null) {
            merged.setTipMessageParams(override.getTipMessageParams());
        }
        if (override.getModelOptions() != null) {
            merged.setModelOptions(override.getModelOptions());
        }
        return merged;
    }

    /**
     * 请求配置
     */
    public record RequestConfig(@NonNull String question, @NonNull String sessionId,
                                @Nullable ExtendedSendOptions options,
                                @Nullable Duration timeout) {
    }
}
