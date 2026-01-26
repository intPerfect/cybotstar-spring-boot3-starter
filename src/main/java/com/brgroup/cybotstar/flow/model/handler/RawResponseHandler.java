package com.brgroup.cybotstar.flow.model.handler;

import com.brgroup.cybotstar.model.ws.WSResponse;

/**
 * Flow 原始响应事件处理器
 *
 * @author zhiyuan.xi
 */
@FunctionalInterface
public interface RawResponseHandler {
    /**
     * 处理原始响应事件
     *
     * @param response WebSocket 响应
     */
    void handle(WSResponse response);
}
