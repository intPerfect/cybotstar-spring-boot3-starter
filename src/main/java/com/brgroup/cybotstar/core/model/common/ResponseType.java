package com.brgroup.cybotstar.core.model.common;

/**
 * WebSocket 响应类型枚举
 * 用于标识不同类型的响应消息
 *
 * @author zhiyuan.xi
 */
public enum ResponseType {
    /**
     * 心跳响应
     */
    HEARTBEAT("heartbeat"),

    /**
     * LLM 结束
     */
    LLM_END("llm_end"),

    /**
     * Flow 类型
     */
    FLOW("flow"),

    /**
     * 文本类型
     */
    TEXT("text");

    private final String value;

    ResponseType(String value) {
        this.value = value;
    }

    /**
     * 获取响应类型值
     *
     * @return 响应类型字符串
     */
    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值查找对应的枚举
     *
     * @param value 字符串值
     * @return 对应的枚举，如果不存在则返回 null
     */
    public static ResponseType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ResponseType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 检查给定的响应类型是否为指定类型
     *
     * @param value 响应类型字符串
     * @param type  要检查的枚举类型
     * @return 如果匹配返回 true，否则返回 false
     */
    public static boolean isType(String value, ResponseType type) {
        return type != null && type.value.equals(value);
    }
}

