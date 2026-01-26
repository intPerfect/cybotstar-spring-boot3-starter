package com.brgroup.cybotstar.flow.model.vo;

import com.alibaba.fastjson2.annotation.JSONType;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Flow 消息事件 VO
 * 包含消息事件的有意义字段，整合了 FlowMessageContent 和 FlowData 的字段
 *
 * @author zhiyuan.xi
 */
@Data
@JSONType(orders = { "displayText", "isFinished", "answerIndex", "nodeId", "flowName", "nodeTitle", "nodeType",
        "message", "flowStage", "curNodeId", "parentNodeId", "variables" })
public class FlowMessageVO {
    /**
     * 显示文本（来自 FlowMessageContent）
     */
    @Nullable
    private String displayText;

    /**
     * 是否完成（来自 FlowMessageContent）
     */
    private boolean isFinished;

    /**
     * 当前分片序号（来自 FlowMessageContent）
     */
    @Nullable
    private Integer answerIndex;

    /**
     * 当前节点ID（来自 FlowMessageContent）
     */
    @Nullable
    private String nodeId;

    /**
     * 当前运行对话流名称（来自 FlowData）
     */
    @Nullable
    private String flowName;

    /**
     * 当前运行结点名称（来自 FlowData）
     */
    @Nullable
    private String nodeTitle;

    /**
     * 当前运行结点类型（来自 FlowData）
     */
    @Nullable
    private String nodeType;

    /**
     * 状态说明（来自 FlowData）
     */
    @Nullable
    private String message;

    /**
     * 当前 flow 的状态（来自 FlowData）
     */
    @Nullable
    private String flowStage;

    /**
     * 当前结点的 id（来自 FlowData）
     */
    @Nullable
    private String curNodeId;

    /**
     * 当前结点的父结点 id（来自 FlowData）
     */
    @Nullable
    private String parentNodeId;

    /**
     * 变量信息（从 output.variables 中提取，来自 FlowData）
     */
    @Nullable
    private Map<String, Object> variables;
}
