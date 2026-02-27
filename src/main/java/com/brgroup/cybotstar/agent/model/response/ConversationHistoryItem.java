package com.brgroup.cybotstar.agent.model.response;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 会话历史项
 *
 * @author zhiyuan.xi
 */
@Data
public class ConversationHistoryItem {
    @JSONField(name = "chat_count")
    private Integer chatCount;

    @JSONField(name = "segment_code")
    private String segmentCode;

    @JSONField(name = "segment_name")
    private String segmentName;

    @JSONField(name = "user_code")
    private String userCode;

    @JSONField(name = "create_time")
    private String createTime;

    @JSONField(name = "message_source")
    private String messageSource;
}

