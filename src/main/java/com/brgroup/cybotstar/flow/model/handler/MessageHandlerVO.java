package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.flow.model.vo.FlowMessageVO;

/**
 * Flow 消息事件处理器（接收 VO）
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface MessageHandlerVO {
    /**
     * 处理消息事件
     *
     * @param vo FlowMessageVO，包含消息事件的有意义字段
     */
    void handle(FlowMessageVO vo);
}
