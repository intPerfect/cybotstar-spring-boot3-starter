package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.vo.FlowErrorVO;

/**
 * Flow 错误事件处理器（接收 VO）
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface ErrorHandlerVO {
    /**
     * 处理错误事件
     *
     * @param vo FlowErrorVO，包含错误事件的有意义字段
     */
    void handle(FlowErrorVO vo);
}
