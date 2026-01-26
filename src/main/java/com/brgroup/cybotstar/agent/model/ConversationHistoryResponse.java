package com.brgroup.cybotstar.agent.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

/**
 * 获取会话历史的响应数据
 *
 * @author zhiyuan.xi
 */
@Data
public class ConversationHistoryResponse {
    /**
     * 总记录数
     */
    private Integer total;

    /**
     * 当前页码
     */
    private Integer page;

    @JSONField(name = "page_size")
    private Integer pageSize;

    @JSONField(name = "has_next")
    private Boolean hasNext;

    @JSONField(name = "has_previous")
    private Boolean hasPrevious;

    @JSONField(name = "max_page_num")
    private Integer maxPageNum;

    /**
     * 会话列表
     */
    private List<ConversationHistoryItem> list;
}

