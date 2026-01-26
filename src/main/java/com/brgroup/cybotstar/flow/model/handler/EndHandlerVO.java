package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.vo.FlowEndVO;

/**
 * Flow 结束事件处理器（接收 VO）
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface EndHandlerVO {
    /**
     * 处理结束事件
     *
     * @param vo FlowEndVO，包含结束事件的有意义字段
     */
    void handle(FlowEndVO vo);
}
