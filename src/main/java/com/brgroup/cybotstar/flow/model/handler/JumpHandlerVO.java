package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.vo.FlowJumpVO;

/**
 * Flow 跳转事件处理器（接收 VO）
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface JumpHandlerVO {
    /**
     * 处理跳转事件
     *
     * @param vo FlowJumpVO，包含跳转事件的有意义字段
     */
    void handle(FlowJumpVO vo);
}
