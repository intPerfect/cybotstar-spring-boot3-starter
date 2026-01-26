package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.FlowData;

/**
 * Flow 节点进入事件处理器
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface NodeEnterHandler {
    /**
     * 处理节点进入事件
     *
     * @param flowData Flow 响应数据，包含节点的所有信息
     */
    void handle(FlowData flowData);
}
