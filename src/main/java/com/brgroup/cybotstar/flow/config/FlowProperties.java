package com.brgroup.cybotstar.flow.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Flow 运行时配置属性
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowProperties {
    /**
     * 流程触发方式，auto兼容正常对话和意图识别；direct需指定具体flow_uuid
     */
    @Builder.Default
    private String openFlowTrigger = "direct";

    /**
     * 指定运行的具体flow流，open_flow_trigger为direct时必填
     */
    private String openFlowUuid;

    /**
     * 指定flow中的某个具体node运行
     */
    private String openFlowNodeUuid;

    /**
     * 指定flow中某个具体node的入参
     */
    private Map<String, Object> openFlowNodeInputs;

    /**
     * 是否开启debug模式，true是，false否；默认false
     */
    @Builder.Default
    private Boolean openFlowDebug = false;
}
