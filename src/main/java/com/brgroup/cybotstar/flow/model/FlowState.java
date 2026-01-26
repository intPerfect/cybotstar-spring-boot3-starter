package com.brgroup.cybotstar.flow.model;

import lombok.Getter;

/**
 * Flow 运行状态枚举
 * <p>
 * - IDLE: 未启动
 * - STARTING: 启动中
 * - RUNNING: 运行中
 * - WAITING: 等待用户输入
 * - COMPLETED: 已完成
 * - ERROR: 错误状态
 * - ABORTED: 已中止
 *
 * @author zhiyuan.xi
 */
@Getter
public enum FlowState {
    /**
     * 未启动
     */
    IDLE("idle"),

    /**
     * 启动中
     */
    STARTING("starting"),

    /**
     * 运行中
     */
    RUNNING("running"),

    /**
     * 等待用户输入
     */
    WAITING("waiting"),

    /**
     * 已完成
     */
    COMPLETED("completed"),

    /**
     * 错误状态
     */
    ERROR("error"),

    /**
     * 已中止
     */
    ABORTED("aborted");

    private final String value;

    FlowState(String value) {
        this.value = value;
    }

    public static FlowState fromValue(String value) {
        for (FlowState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        return IDLE;
    }
}

