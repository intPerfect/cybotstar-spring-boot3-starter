package com.brgroup.cybotstar.model.common;

/**
 * 会话状态枚举
 *
 * @author zhiyuan.xi
 */
public enum SessionState {
    /**
     * 未开始
     */
    IDLE("idle"),

    /**
     * 已连接
     */
    CONNECTED("connected"),

    /**
     * 对话中
     */
    CHATTING("chatting"),

    /**
     * 等待响应
     */
    WAITING("waiting"),

    /**
     * 已关闭
     */
    CLOSED("closed");

    private final String value;

    SessionState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SessionState fromValue(String value) {
        for (SessionState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        return IDLE;
    }
}

