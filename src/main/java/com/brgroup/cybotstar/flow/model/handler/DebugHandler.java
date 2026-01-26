package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.FlowData;

/**
 * Flow 调试信息事件处理器
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface DebugHandler {
    /**
     * 处理调试信息事件
     *
     * @param flowData 完整的 Flow 响应数据
     */
    void handle(FlowData flowData);
}
