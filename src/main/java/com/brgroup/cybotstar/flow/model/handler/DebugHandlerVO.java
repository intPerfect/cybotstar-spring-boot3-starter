package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.vo.FlowDebugVO;

/**
 * Flow 调试信息事件处理器（接收 VO）
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface DebugHandlerVO {
    /**
     * 处理调试信息事件
     *
     * @param vo FlowDebugVO，包含调试信息事件的有意义字段
     */
    void handle(FlowDebugVO vo);
}
