package com.brgroup.cybotstar.agent.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息参数（同 OpenAI 接口 message）
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageParam {
    /**
     * 角色：system, user, assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 对话 ID（可选）
     * 用于标识该消息所属的对话
     * 在序列化为 JSON 时，如果用于 messageParams，此字段会被忽略
     */
    @JSONField(serialize = false)
    private String dialogId;

    /**
     * 创建 system 消息
     * 
     * @param content 消息内容
     * @return MessageParam 对象
     */
    public static MessageParam system(String content) {
        return MessageParam.builder()
                .role("system")
                .content(content)
                .build();
    }

    /**
     * 创建 user 消息
     * 
     * @param content 消息内容
     * @return MessageParam 对象
     */
    public static MessageParam user(String content) {
        return MessageParam.builder()
                .role("user")
                .content(content)
                .build();
    }

    /**
     * 创建 assistant 消息
     * 
     * @param content 消息内容
     * @return MessageParam 对象
     */
    public static MessageParam assistant(String content) {
        return MessageParam.builder()
                .role("assistant")
                .content(content)
                .build();
    }
}

