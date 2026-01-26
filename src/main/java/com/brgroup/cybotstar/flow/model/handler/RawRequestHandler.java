package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.model.ws.WSPayload;

/**
 * Flow 原始请求事件处理器
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface RawRequestHandler {
    /**
     * 处理原始请求事件
     *
     * @param request WebSocket 请求载荷
     */
    void handle(WSPayload request);
}
