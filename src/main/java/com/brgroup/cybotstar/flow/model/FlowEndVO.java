package com.brgroup.cybotstar.flow.model;

import com.alibaba.fastjson2.annotation.JSONType;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Flow 结束事件 VO
 * 包含结束事件的有意义字段
 *
 * @author zhiyuan.xi
 */
@Data
@JSONType(orders = {"finalText", "flowName", "nodeTitle", "nodeType", "message", "flowStage", "curNodeId", "parentNodeId", "variables"})
public class FlowEndVO {
    /**
     * 最终文本
     */
    @Nullable
    private String finalText;

    /**
     * 当前运行对话流名称
     */
    @Nullable
    private String flowName;

    /**
     * 当前运行结点名称
     */
    @Nullable
    private String nodeTitle;

    /**
     * 当前运行结点类型
     */
    @Nullable
    private String nodeType;

    /**
     * 状态说明
     */
    @Nullable
    private String message;

    /**
     * 当前 flow 的状态
     */
    @Nullable
    private String flowStage;

    /**
     * 当前结点的 id
     */
    @Nullable
    private String curNodeId;

    /**
     * 当前结点的父结点 id
     */
    @Nullable
    private String parentNodeId;

    /**
     * 变量信息（从 output.variables 中提取）
     */
    @Nullable
    private Map<String, Object> variables;
}
