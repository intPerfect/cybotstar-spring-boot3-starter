package com.brgroup.cybotstar.core.model.common;

import lombok.Getter;

/**
 * 连接状态枚举
 *
 * @author zhiyuan.xi
 */
@Getter
public enum ConnectionState {
    /**
     * 未连接
     */
    DISCONNECTED("disconnected"),

    /**
     * 连接中
     */
    CONNECTING("connecting"),

    /**
     * 已连接
     */
    CONNECTED("connected"),

    /**
     * 重连中
     */
    RECONNECTING("reconnecting"),

    /**
     * 已关闭
     */
    CLOSED("closed"),

    /**
     * 不存在
     */
    NOT_EXIST("not_exist");

    private final String value;

    ConnectionState(String value) {
        this.value = value;
    }

    public static ConnectionState fromValue(String value) {
        for (ConnectionState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        return DISCONNECTED;
    }
}

