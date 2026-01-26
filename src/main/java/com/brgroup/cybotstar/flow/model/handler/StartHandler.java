package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.FlowData;

/**
 * Flow 启动事件处理器
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface StartHandler {
    /**
     * 处理启动事件
     *
     * @param flowData 完整的 Flow 响应数据
     */
    void handle(FlowData flowData);
}
