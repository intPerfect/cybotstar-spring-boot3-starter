package com.brgroup.cybotstar.core.model.common;

import java.util.Arrays;
import java.util.Optional;

/**
 * WebSocket 响应索引类型枚举
 * 用于标识不同类型的响应消息
 *
 * @author zhiyuan.xi
 */
public enum ResponseIndex {
    /**
     * 提问已确认
     */
    MESSAGE_CONFIRMED(-1),

    /**
     * 线程信息
     */
    THREAD_INFO(-2),

    /**
     * 联网搜索结果
     */
    ONLINE_SEARCH(-3),

    /**
     * 引用图片
     */
    IMAGE_REFERENCE(-4),

    /**
     * Reasoning（思考内容）
     */
    REASONING(-8);

    private final int value;

    ResponseIndex(int value) {
        this.value = value;
    }

    /**
     * 获取索引值
     *
     * @return 索引值
     */
    public int getValue() {
        return value;
    }

    /**
     * 根据整数值查找对应的枚举
     *
     * @param value 整数值
     * @return 对应的枚举，如果不存在则返回 Optional.empty()
     */
    public static Optional<ResponseIndex> fromValue(int value) {
        return Arrays.stream(values())
                .filter(index -> index.value == value)
                .findFirst();
    }

    /**
     * 检查给定的索引值是否为特殊索引（负数）
     *
     * @param index 索引值
     * @return 如果是特殊索引返回 true，否则返回 false
     */
    public static boolean isSpecialIndex(Integer index) {
        return index != null && index < 0 && fromValue(index).isPresent();
    }
}

