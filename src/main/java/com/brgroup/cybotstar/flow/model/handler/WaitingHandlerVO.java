package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.vo.FlowWaitingVO;

/**
 * Flow 等待输入事件处理器（接收 VO）
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface WaitingHandlerVO {
    /**
     * 处理等待输入事件
     *
     * @param vo FlowWaitingVO，包含等待输入事件的有意义字段
     */
    void handle(FlowWaitingVO vo);
}
