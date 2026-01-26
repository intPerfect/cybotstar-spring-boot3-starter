package com.brgroup.cybotstar.flow.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Flow 事件类型枚举
 *
 * 定义了 Flow 客户端支持的所有事件类型
 *
 * @author zhiyuan.xi
 */
@Getter
public enum FlowEventType {
    /**
     * Flow 启动事件
     * 对应服务端 event code: 002000
     */
    START("start", "002000"),

    /**
     * 消息事件
     * 由多个 event code 触发：000000（成功响应）、002004（等待输入）
     */
    MESSAGE("message", null),

    /**
     * 等待用户输入事件
     * 对应服务端 event code: 002004
     */
    WAITING("waiting", "002004"),

    /**
     * Flow 结束事件
     * 对应服务端 event code: 002001
     */
    END("end", "002001"),

    /**
     * 错误事件
     * 对应服务端 event code: 400000
     */
    ERROR("error", "400000"),

    /**
     * 调试信息事件
     * 对应服务端 event code: 002003
     */
    DEBUG("debug", "002003"),

    /**
     * 节点进入事件
     * 对应服务端 event code: 002002
     */
    NODE_ENTER("nodeEnter", "002002"),

    /**
     * 跳转事件
     * 对应服务端 event code: 002007
     */
    JUMP("jump", "002007"),

    /**
     * 成功响应事件
     * 对应服务端 event code: 000000
     */
    SUCCESS("success", "000000"),

    /**
     * 轮次完成事件
     * 对应服务端 event code: 002005
     */
    ROUND_COMPLETE("roundComplete", "002005"),

    /**
     * 原始响应事件
     * 客户端内部事件，无对应服务端 event code
     */
    RAW_RESPONSE("rawResponse", null),

    /**
     * 原始请求事件
     * 客户端内部事件，无对应服务端 event code
     */
    RAW_REQUEST("rawRequest", null),

    /**
     * 连接建立事件
     * 客户端内部事件，无对应服务端 event code
     */
    CONNECTED("connected", null),

    /**
     * 连接断开事件
     * 客户端内部事件，无对应服务端 event code
     */
    DISCONNECTED("disconnected", null),

    /**
     * 重连事件
     * 客户端内部事件，无对应服务端 event code
     */
    RECONNECTING("reconnecting", null);

    /**
     * 事件类型的字符串值
     */
    private final String value;

    /**
     * 服务端 event code
     * 如果为 null，表示该事件类型没有对应的服务端 event code（客户端内部事件）
     */
    private final String eventCode;

    /**
     * Event code 到枚举值的映射缓存
     */
    private static final Map<String, FlowEventType> EVENT_CODE_MAP = Arrays.stream(values())
            .filter(type -> type.eventCode != null)
            .collect(Collectors.toMap(FlowEventType::getEventCode, type -> type));

    FlowEventType(String value, String eventCode) {
        this.value = value;
        this.eventCode = eventCode;
    }

    /**
     * 根据服务端 event code 查找对应的 FlowEventType
     *
     * @param eventCode 服务端 event code
     * @return 对应的 FlowEventType，如果不存在则返回 null
     */
    public static FlowEventType fromEventCode(String eventCode) {
        if (eventCode == null || eventCode.isEmpty()) {
            return null;
        }
        return EVENT_CODE_MAP.get(eventCode);
    }

}
