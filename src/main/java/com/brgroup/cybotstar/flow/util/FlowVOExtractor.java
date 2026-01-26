package com.brgroup.cybotstar.flow.util;

import com.brgroup.cybotstar.flow.model.FlowData;
import com.brgroup.cybotstar.flow.model.vo.FlowStartVO;
import com.brgroup.cybotstar.flow.model.vo.FlowNodeEnterVO;
import com.brgroup.cybotstar.flow.model.vo.FlowMessageVO;
import com.brgroup.cybotstar.flow.model.vo.FlowWaitingVO;
import com.brgroup.cybotstar.flow.model.vo.FlowEndVO;
import com.brgroup.cybotstar.flow.model.vo.FlowErrorVO;
import com.brgroup.cybotstar.flow.model.vo.FlowDebugVO;
import com.brgroup.cybotstar.flow.model.vo.FlowJumpVO;

/**
 * Flow VO 提取工具类
 * 用于从 FlowData 中提取有意义的字段，封装为 VO
 *
 * @author zhiyuan.xi
 */
public class FlowVOExtractor {

    /**
     * 从 FlowData 中提取 FlowStartVO
     *
     * @param flowData Flow 响应数据
     * @return FlowStartVO，如果 flowData 为 null 则返回 null
     */
    public static FlowStartVO extractFlowStartVO(FlowData flowData) {
        if (flowData == null) {
            return null;
        }

        FlowStartVO vo = new FlowStartVO();
        vo.setFlowName(flowData.getFlowName());
        vo.setNodeType(flowData.getNodeType());
        vo.setNodeTitle(flowData.getNodeTitle());
        vo.setMessage(flowData.getMessage());

        // 从 data 字段中提取
        FlowData.MessageData messageData = flowData.getData();
        if (messageData != null) {
            vo.setFlowStage(messageData.getFlowStage());
            vo.setCurNodeId(messageData.getCurNodeId());
            vo.setParentNodeId(messageData.getParentNodeId());
        }

        return vo;
    }

    /**
     * 从 FlowData 中提取 FlowNodeEnterVO
     *
     * @param flowData Flow 响应数据
     * @return FlowNodeEnterVO，如果 flowData 为 null 则返回 null
     */
    public static FlowNodeEnterVO extractFlowNodeEnterVO(FlowData flowData) {
        if (flowData == null) {
            return null;
        }

        FlowNodeEnterVO vo = new FlowNodeEnterVO();
        vo.setFlowName(flowData.getFlowName());
        vo.setNodeType(flowData.getNodeType());
        vo.setNodeTitle(flowData.getNodeTitle());
        vo.setMessage(flowData.getMessage());

        // 从 data 字段中提取
        FlowData.MessageData messageData = flowData.getData();
        if (messageData != null) {
            vo.setFlowStage(messageData.getFlowStage());
            vo.setCurNodeId(messageData.getCurNodeId());
            vo.setParentNodeId(messageData.getParentNodeId());

            // 提取变量信息
            if (messageData.getOutput() != null && messageData.getOutput().getVariables() != null) {
                vo.setVariables(messageData.getOutput().getVariables());
            }
        }

        return vo;
    }

    /**
     * 从 FlowData 中提取 FlowMessageVO
     *
     * @param flowData Flow 响应数据
     * @return FlowMessageVO，如果 flowData 为 null 则返回 null
     */
    public static FlowMessageVO extractFlowMessageVO(FlowData flowData) {
        if (flowData == null) {
            return null;
        }

        FlowMessageVO vo = new FlowMessageVO();

        // 从 FlowData 中提取字段
        vo.setFlowName(flowData.getFlowName());
        vo.setNodeType(flowData.getNodeType());
        vo.setNodeTitle(flowData.getNodeTitle());
        vo.setMessage(flowData.getMessage());

        // 从 data 字段中提取
        FlowData.MessageData messageData = flowData.getData();
        if (messageData != null) {
            // 提取显示文本
            String displayText = FlowUtils.extractFlowDisplayText(messageData);
            vo.setDisplayText(displayText);
            
            // 提取完成状态
            vo.setFinished(FlowUtils.isMessageFinished(messageData));
            
            // 提取分片序号
            vo.setAnswerIndex(FlowUtils.getAnswerIndex(messageData));
            
            // 提取节点ID
            vo.setNodeId(messageData.getCurNodeId());
            
            // 提取其他字段
            vo.setFlowStage(messageData.getFlowStage());
            vo.setCurNodeId(messageData.getCurNodeId());
            vo.setParentNodeId(messageData.getParentNodeId());

            // 提取变量信息
            if (messageData.getOutput() != null && messageData.getOutput().getVariables() != null) {
                vo.setVariables(messageData.getOutput().getVariables());
            }
        }

        return vo;
    }

    /**
     * 从 FlowData 中提取 FlowWaitingVO
     *
     * @param flowData Flow 响应数据
     * @return FlowWaitingVO，如果 flowData 为 null 则返回 null
     */
    public static FlowWaitingVO extractFlowWaitingVO(FlowData flowData) {
        if (flowData == null) {
            return null;
        }

        FlowWaitingVO vo = new FlowWaitingVO();
        vo.setFlowName(flowData.getFlowName());
        vo.setNodeType(flowData.getNodeType());
        vo.setNodeTitle(flowData.getNodeTitle());
        vo.setMessage(flowData.getMessage());

        FlowData.MessageData messageData = flowData.getData();
        if (messageData != null) {
            vo.setFlowStage(messageData.getFlowStage());
            vo.setCurNodeId(messageData.getCurNodeId());
            vo.setParentNodeId(messageData.getParentNodeId());

            if (messageData.getOutput() != null && messageData.getOutput().getVariables() != null) {
                vo.setVariables(messageData.getOutput().getVariables());
            }
        }

        return vo;
    }

    /**
     * 从 FlowData 中提取 FlowEndVO
     *
     * @param flowData Flow 响应数据
     * @param finalText 最终文本
     * @return FlowEndVO，如果 flowData 为 null 则返回 null
     */
    public static FlowEndVO extractFlowEndVO(FlowData flowData, String finalText) {
        if (flowData == null) {
            return null;
        }

        FlowEndVO vo = new FlowEndVO();
        vo.setFinalText(finalText);
        vo.setFlowName(flowData.getFlowName());
        vo.setNodeType(flowData.getNodeType());
        vo.setNodeTitle(flowData.getNodeTitle());
        vo.setMessage(flowData.getMessage());

        FlowData.MessageData messageData = flowData.getData();
        if (messageData != null) {
            vo.setFlowStage(messageData.getFlowStage());
            vo.setCurNodeId(messageData.getCurNodeId());
            vo.setParentNodeId(messageData.getParentNodeId());

            if (messageData.getOutput() != null && messageData.getOutput().getVariables() != null) {
                vo.setVariables(messageData.getOutput().getVariables());
            }
        }

        return vo;
    }

    /**
     * 从 FlowData 中提取 FlowErrorVO
     *
     * @param flowData Flow 响应数据
     * @param error 错误异常
     * @return FlowErrorVO，如果 flowData 为 null 则返回 null
     */
    public static FlowErrorVO extractFlowErrorVO(FlowData flowData, Exception error) {
        if (flowData == null) {
            return null;
        }

        FlowErrorVO vo = new FlowErrorVO();
        vo.setErrorMessage(error != null ? error.getMessage() : flowData.getMessage());
        vo.setFlowName(flowData.getFlowName());
        vo.setNodeType(flowData.getNodeType());
        vo.setNodeTitle(flowData.getNodeTitle());
        vo.setMessage(flowData.getMessage());

        FlowData.MessageData messageData = flowData.getData();
        if (messageData != null) {
            vo.setFlowStage(messageData.getFlowStage());
            vo.setCurNodeId(messageData.getCurNodeId());
            vo.setParentNodeId(messageData.getParentNodeId());

            if (messageData.getOutput() != null && messageData.getOutput().getVariables() != null) {
                vo.setVariables(messageData.getOutput().getVariables());
            }
        }

        return vo;
    }

    /**
     * 从 FlowData 中提取 FlowDebugVO
     *
     * @param flowData Flow 响应数据
     * @param debugInfo 调试信息
     * @return FlowDebugVO，如果 flowData 为 null 则返回 null
     */
    public static FlowDebugVO extractFlowDebugVO(FlowData flowData, String debugInfo) {
        if (flowData == null) {
            return null;
        }

        FlowDebugVO vo = new FlowDebugVO();
        vo.setDebugInfo(debugInfo);
        vo.setFlowName(flowData.getFlowName());
        vo.setNodeType(flowData.getNodeType());
        vo.setNodeTitle(flowData.getNodeTitle());
        vo.setMessage(flowData.getMessage());

        FlowData.MessageData messageData = flowData.getData();
        if (messageData != null) {
            vo.setFlowStage(messageData.getFlowStage());
            vo.setCurNodeId(messageData.getCurNodeId());
            vo.setParentNodeId(messageData.getParentNodeId());

            if (messageData.getOutput() != null && messageData.getOutput().getVariables() != null) {
                vo.setVariables(messageData.getOutput().getVariables());
            }
        }

        return vo;
    }

    /**
     * 从 FlowData 中提取 FlowJumpVO
     *
     * @param flowData Flow 响应数据
     * @param jumpType 跳转类型
     * @return FlowJumpVO，如果 flowData 为 null 则返回 null
     */
    public static FlowJumpVO extractFlowJumpVO(FlowData flowData, String jumpType) {
        if (flowData == null) {
            return null;
        }

        FlowJumpVO vo = new FlowJumpVO();
        vo.setJumpType(jumpType);
        vo.setFlowName(flowData.getFlowName());
        vo.setNodeType(flowData.getNodeType());
        vo.setNodeTitle(flowData.getNodeTitle());
        vo.setMessage(flowData.getMessage());

        FlowData.MessageData messageData = flowData.getData();
        if (messageData != null) {
            vo.setFlowStage(messageData.getFlowStage());
            vo.setCurNodeId(messageData.getCurNodeId());
            vo.setParentNodeId(messageData.getParentNodeId());

            if (messageData.getOutput() != null && messageData.getOutput().getVariables() != null) {
                vo.setVariables(messageData.getOutput().getVariables());
            }
        }

        return vo;
    }
}
