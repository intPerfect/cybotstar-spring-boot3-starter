package com.brgroup.cybotstar.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型参数配置
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelOptions {
    /**
     * 采样温度 0-1
     */
    private Double topP;

    /**
     * 随机性 0-2
     */
    private Double temperature;

    /**
     * 频率惩罚
     */
    private Double frequencyPenalty;

    /**
     * 存在惩罚
     */
    private Double presencePenalty;

    /**
     * 最大 token 数
     */
    private Integer maxTokens;
}

