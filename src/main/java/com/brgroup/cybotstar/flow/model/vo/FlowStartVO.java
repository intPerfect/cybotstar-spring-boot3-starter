package com.brgroup.cybotstar.flow.model.vo;

import com.alibaba.fastjson2.annotation.JSONType;
import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * Flow 启动事件 VO（Value Object）
 * 包含 FlowStart 事件中有实际意义的字段
 *
 * @author zhiyuan.xi
 */
@Data
@JSONType(orders = { "flowName", "nodeTitle", "nodeType", "message", "flowStage", "curNodeId", "parentNodeId" })
public class FlowStartVO {
    /**
     * 对话流名称
     */
    @Nullable
    private String flowName;

    /**
     * 节点标题
     */
    @Nullable
    private String nodeTitle;

    /**
     * 节点类型（如 "start"）
     */
    @Nullable
    private String nodeType;

    /**
     * 状态说明
     */
    @Nullable
    private String message;

    /**
     * Flow 阶段（如 "flow_enter"）
     */
    @Nullable
    private String flowStage;

    /**
     * 当前节点 ID
     */
    @Nullable
    private String curNodeId;

    /**
     * 父节点 ID
     */
    @Nullable
    private String parentNodeId;
}
