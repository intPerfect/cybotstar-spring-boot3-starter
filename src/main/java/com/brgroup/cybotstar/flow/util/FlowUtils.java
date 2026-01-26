package com.brgroup.cybotstar.flow.util;

import com.brgroup.cybotstar.flow.model.FlowData;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Flow 消息处理工具函数
 *
 * 提供 Flow 消息解析和处理的工具函数
 *
 * @author zhiyuan.xi
 */
public class FlowUtils {

    /**
     * 系统消息列表
     */
    private static final List<String> SYSTEM_MESSAGES = Arrays.asList(
            "flow_enter",
            "node_waiting_input",
            "current_communication_complete",
            "node_id:",
            "enter by prev_node_id:",
            "entity:",
            "llm response:"
    );

    /**
     * 判断是否为系统消息
     *
     * @param message 消息内容
     * @return 是否为系统消息
     */
    public static boolean isFlowSystemMessage(String message) {
        if (StringUtils.isBlank(message)) {
            return false;
        }
        return SYSTEM_MESSAGES.stream().anyMatch(message::contains);
    }

    /**
     * 提取 Flow 显示文本
     *
     * 从 Flow 数据中提取要显示给用户的文本
     *
     * @param messageData Flow 消息数据
     * @return 显示文本，如果无法提取则返回 null
     */
    public static String extractFlowDisplayText(FlowData.MessageData messageData) {
        if (messageData == null) {
            return null;
        }

        String answer = messageData.getAnswer() != null ? messageData.getAnswer() : "";
        FlowData.MessageData.FlowOutput output = messageData.getOutput();

        boolean isSystemMessage = isFlowSystemMessage(answer);

        String displayText = "";

        if (output != null) {
            String robotUserReplying = output.getRobotUserReplying();
            if (robotUserReplying != null && !robotUserReplying.trim().isEmpty()) {
                displayText = robotUserReplying;
            } else {
                String robotUserAsking = output.getRobotUserAsking();
                if (robotUserAsking != null && !robotUserAsking.trim().isEmpty()) {
                    displayText = robotUserAsking;
                }
            }
        }

        if (StringUtils.isBlank(displayText) && StringUtils.isNotBlank(answer) && !isSystemMessage) {
            displayText = answer;
        }

        if (StringUtils.isBlank(displayText) || isSystemMessage) {
            return null;
        }

        return displayText;
    }

    /**
     * 检查消息是否完成
     *
     * @param messageData Flow 消息数据
     * @return 是否完成
     */
    public static boolean isMessageFinished(FlowData.MessageData messageData) {
        if (messageData == null) {
            return true;
        }
        Integer nodeStream = messageData.getNodeStream() != null ? messageData.getNodeStream() : 0;
        String nodeAnswerFinish = messageData.getNodeAnswerFinish();
        boolean isFinished = "y".equals(nodeAnswerFinish);
        return nodeStream != null && nodeStream == 1 ? isFinished : true;
    }

    /**
     * 获取消息的分片序号
     *
     * @param messageData Flow 消息数据
     * @return 分片序号，如果不是流式消息则返回 null
     */
    public static Integer getAnswerIndex(FlowData.MessageData messageData) {
        if (messageData == null) {
            return null;
        }
        Integer nodeStream = messageData.getNodeStream() != null ? messageData.getNodeStream() : 0;
        return nodeStream != null && nodeStream == 1 ? messageData.getNodeAnswerIndex() : null;
    }
}

