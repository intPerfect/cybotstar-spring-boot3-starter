package com.brgroup.cybotstar.core.model.ws;

import com.alibaba.fastjson2.annotation.JSONField;
import com.brgroup.cybotstar.agent.model.response.ChatHistoryItem;
import com.brgroup.cybotstar.agent.model.request.MessageParam;
import com.brgroup.cybotstar.agent.model.ModelOptions;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 发送载荷
 *
 * @author zhiyuan.xi
 */
@Data
public class WSPayload {
    @JSONField(name = "cybertron-robot-key")
    private String cybertronRobotKey;

    @JSONField(name = "cybertron-robot-token")
    private String cybertronRobotToken;

    private String username;
    private String question;

    @JSONField(name = "open_flow_trigger")
    private String openFlowTrigger;

    @JSONField(name = "open_flow_uuid")
    private String openFlowUuid;

    @JSONField(name = "open_flow_node_uuid")
    private String openFlowNodeUuid;

    @JSONField(name = "open_flow_node_inputs")
    private Map<String, Object> openFlowNodeInputs;

    @JSONField(name = "open_flow_debug")
    private Integer openFlowDebug;

    @JSONField(name = "segment_code")
    private String segmentCode;

    @JSONField(name = "extra-header")
    private String extraHeader;

    @JSONField(name = "extra-body")
    private String extraBody;

    @JSONField(name = "message_params")
    private List<MessageParam> messageParams;

    @JSONField(name = "chat_history")
    private List<ChatHistoryItem> chatHistory;

    @JSONField(name = "tip_message_extra")
    private String tipMessageExtra;

    @JSONField(name = "tip_message_params")
    private Map<String, String> tipMessageParams;

    @JSONField(name = "model_params")
    private ModelOptions modelOptions;
}

