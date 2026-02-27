package com.brgroup.cybotstar.agent.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话历史项
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryItem {
    /**
     * 问题
     */
    private String question;

    /**
     * 回答
     */
    private String answer;
}

