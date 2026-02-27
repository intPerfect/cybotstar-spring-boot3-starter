package com.brgroup.cybotstar.agent.model.request;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 获取会话历史的请求实体
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetConversationHistoryRequest {
    /**
     * 用户名（必填）
     */
    private String username;

    /**
     * 过滤模式，默认为 0
     */
    @JSONField(name = "filter_mode")
    private Integer filterMode;

    /**
     * 过滤用户代码
     */
    @JSONField(name = "filter_user_code")
    private String filterUserCode;

    /**
     * 创建开始时间，ISO 8601 格式
     */
    @JSONField(name = "create_start_time")
    private String createStartTime;

    /**
     * 创建结束时间，ISO 8601 格式
     */
    @JSONField(name = "create_end_time")
    private String createEndTime;

    /**
     * 消息来源
     */
    @JSONField(name = "message_source")
    private String messageSource;

    /**
     * 会话代码列表
     */
    @JSONField(name = "segment_code_list")
    private List<String> segmentCodeList;

    /**
     * 页码，默认为 1
     */
    @Builder.Default
    private Integer page = 1;

    /**
     * 每页大小，默认为 10
     */
    @Builder.Default
    private Integer pagesize = 10;
}

