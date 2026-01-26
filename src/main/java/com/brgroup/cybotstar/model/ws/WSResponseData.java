package com.brgroup.cybotstar.model.ws;

import lombok.Data;

/**
 * WebSocket 响应数据（通用）
 *
 * @author zhiyuan.xi
 */
@Data
public class WSResponseData {
    /**
     * 回答内容
     */
    private String answer;

    /**
     * 其他数据
     */
    private Object data;
}

