package com.brgroup.cybotstar.agent.model;

import lombok.Data;

import java.util.List;

/**
 * 获取会话历史的请求参数
 *
 * @author zhiyuan.xi
 */
@Data
public class GetConversationHistoryOptions {
    /**
     * 过滤模式，默认为 0
     */
    private Integer filterMode;

    /**
     * 过滤用户代码
     */
    private String filterUserCode;

    /**
     * 创建开始时间，ISO 8601 格式
     */
    private String createStartTime;

    /**
     * 创建结束时间，ISO 8601 格式
     */
    private String createEndTime;

    /**
     * 消息来源
     */
    private String messageSource;

    /**
     * 会话代码列表
     */
    private List<String> segmentCodeList;

    /**
     * 页码，默认为 1
     */
    private Integer page = 1;

    /**
     * 每页大小，默认为 10
     */
    private Integer pagesize = 10;
}

