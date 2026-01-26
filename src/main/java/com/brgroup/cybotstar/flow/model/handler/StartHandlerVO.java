package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.vo.FlowStartVO;

/**
 * Flow 启动事件处理器（接收 VO）
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface StartHandlerVO {
    /**
     * 处理启动事件
     *
     * @param vo FlowStartVO，包含启动事件的有意义字段
     */
    void handle(FlowStartVO vo);
}
