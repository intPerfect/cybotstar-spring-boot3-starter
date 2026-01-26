package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.vo.FlowNodeEnterVO;

/**
 * Flow 节点进入事件处理器（接收 VO）
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface NodeEnterHandlerVO {
    /**
     * 处理节点进入事件
     *
     * @param vo FlowNodeEnterVO，包含节点进入事件的有意义字段
     */
    void handle(FlowNodeEnterVO vo);
}
