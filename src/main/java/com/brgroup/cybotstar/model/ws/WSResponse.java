package com.brgroup.cybotstar.model.ws;

import com.alibaba.fastjson2.annotation.JSONField;
import com.brgroup.cybotstar.model.common.ResponseIndex;
import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * WebSocket 响应
 *
 * @author zhiyuan.xi
 */
@Data
public class WSResponse {
    /**
     * 响应码: "000000" 为正常，其他异常
     */
    private String code;

    /**
     * 状态说明
     */
    private String message;

    /**
     * 对话 ID
     */
    @JSONField(name = "dialog_id")
    @Nullable
    private String dialogId;

    /**
     * 响应类型
     */
    private String type;

    /**
     * 索引：
     * - 负数: 特殊索引（参见 {@link ResponseIndex}）
     * - -8: 思考过程 ({@link ResponseIndex#REASONING})
     * - -4: 引用图片 ({@link ResponseIndex#IMAGE_REFERENCE})
     * - -3: 联网搜索结果 ({@link ResponseIndex#ONLINE_SEARCH})
     * - -2: 线程信息 ({@link ResponseIndex#THREAD_INFO})
     * - -1: 提问已确认 ({@link ResponseIndex#MESSAGE_CONFIRMED})
     * - 0+: 流式片段序号
     */
    private Integer index;

    /**
     * 响应数据（根据 type 不同有不同结构，联网搜索/图片等场景可能为 string）
     */
    @Nullable
    private Object data;

    /**
     * 是否完成: 'y' 表示回复已结束
     */
    private String finish;

    /**
     * Flow 相关字段
     */
    @JSONField(name = "flow_redis_key")
    private String flowRedisKey;

    @JSONField(name = "cur_node_id")
    private String curNodeId;

    @JSONField(name = "node_id")
    private String nodeId;

    private Integer debug;

    @JSONField(name = "node_developer")
    private Integer nodeDeveloper;

    @JSONField(name = "flow_name")
    private String flowName;

    @JSONField(name = "node_type")
    private String nodeType;

    @JSONField(name = "node_title")
    private String nodeTitle;

    @JSONField(name = "node_waiting_input")
    private Integer nodeWaitingInput;

    @JSONField(name = "parent_node_id")
    private String parentNodeId;
}
