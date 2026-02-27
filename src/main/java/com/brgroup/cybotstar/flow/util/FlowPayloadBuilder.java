package com.brgroup.cybotstar.flow.util;

import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.flow.config.FlowConfig;
import com.brgroup.cybotstar.flow.config.FlowProperties;
import com.brgroup.cybotstar.core.model.ws.WSPayload;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Flow WebSocket Payload 构建器
 * 负责构建 Flow 请求的 WSPayload
 *
 * @author zhiyuan.xi
 */
public class FlowPayloadBuilder {

    /**
     * 构造 Flow 请求载荷
     *
     * @param config  客户端配置
     * @param options Flow 连接选项
     * @param sessionId 会话 ID
     * @return Flow WebSocket 发送载荷
     */
    public static WSPayload buildFlowPayload(AgentConfig config, FlowConfig options, String sessionId) {
        WSPayload payload = new WSPayload();
        payload.setCybertronRobotKey(config.getCredentials().getRobotKey());
        payload.setCybertronRobotToken(config.getCredentials().getRobotToken());
        payload.setUsername(config.getCredentials().getUsername());
        
        // 恢复会话时，如果question为空字符串，则不设置question字段（避免发送空消息）
        // 只有在question不为空时才设置question字段
        String question = options.getQuestion();
        if (question != null && !question.trim().isEmpty()) {
            payload.setQuestion(question);
        }
        // 如果question为null或空字符串，则不设置question字段（恢复会话场景）
        
        // 从 FlowProperties 读取配置
        FlowProperties flowProps = options.getFlow();
        if (flowProps != null) {
            String openFlowTrigger = flowProps.getOpenFlowTrigger() != null 
                    ? flowProps.getOpenFlowTrigger() : "direct";
            payload.setOpenFlowTrigger(openFlowTrigger);
            
            String openFlowUuid = flowProps.getOpenFlowUuid() != null ? flowProps.getOpenFlowUuid() : "";
            payload.setOpenFlowUuid(openFlowUuid);
            
            String openFlowNodeUuid = flowProps.getOpenFlowNodeUuid() != null ? flowProps.getOpenFlowNodeUuid() : "";
            payload.setOpenFlowNodeUuid(openFlowNodeUuid);
            
            Map<String, Object> openFlowNodeInputs = flowProps.getOpenFlowNodeInputs() != null 
                    ? flowProps.getOpenFlowNodeInputs() : Map.of();
            payload.setOpenFlowNodeInputs(openFlowNodeInputs);
            
            // 将 Boolean 转换为 Integer (true -> 1, false -> 0)
            Integer openFlowDebug = flowProps.getOpenFlowDebug() != null && flowProps.getOpenFlowDebug() ? 1 : 0;
            payload.setOpenFlowDebug(openFlowDebug);
        } else {
            // 如果没有 FlowProperties，使用默认值
            payload.setOpenFlowTrigger("direct");
            payload.setOpenFlowUuid("");
            payload.setOpenFlowNodeUuid("");
            payload.setOpenFlowNodeInputs(Map.of());
            payload.setOpenFlowDebug(0);
        }

        if (StringUtils.isNotBlank(sessionId)) {
            payload.setSegmentCode(sessionId);
        }

        return payload;
    }
}
