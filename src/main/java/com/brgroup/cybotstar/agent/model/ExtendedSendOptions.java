package com.brgroup.cybotstar.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 扩展请求选项
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtendedSendOptions {
    /**
     * 额外请求头
     */
    private String extraHeader;

    /**
     * 额外请求体
     */
    private String extraBody;

    /**
     * ⭐传入模型的 message 字段（同 OpenAI 接口）
     */
    private List<MessageParam> messageParams;

    /**
     * 对话历史（拼到 message 中）
     *
     * @deprecated 此字段已废弃，请使用 {@link #messageParams} 替代。
     *             使用 messageParams 可以更灵活地设置 system、user、assistant 消息和历史对话。
     *             此字段仅保留用于兼容性，未来版本可能会移除。
     */
    @Deprecated
    private List<ChatHistoryItem> chatHistory;

    /**
     * 角色设定覆盖
     *
     * @deprecated 此字段已废弃，请使用 {@link #messageParams} 替代。
     *             可以通过 messageParams 设置 role='system' 的消息来实现角色设定。
     *             此字段仅保留用于兼容性，未来版本可能会移除。
     */
    @Deprecated
    private String tipMessageExtra;

    /**
     * 角色设定参数填充
     *
     * @deprecated 此字段已废弃，请使用 {@link #messageParams} 替代。
     *             可以通过 messageParams 设置 role='system' 的消息，并在消息内容中使用变量替换。
     *             此字段仅保留用于兼容性，未来版本可能会移除。
     */
    @Deprecated
    private Map<String, String> tipMessageParams;

    /**
     * 模型参数配置
     */
    private ModelOptions modelOptions;

}
