package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.FlowData;

/**
 * Flow 等待输入事件处理器
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface WaitingHandler {
    /**
     * 处理等待输入事件
     *
     * @param flowData 完整的 Flow 响应数据
     */
    void handle(FlowData flowData);
}
