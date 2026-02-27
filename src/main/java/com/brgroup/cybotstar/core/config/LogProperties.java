package com.brgroup.cybotstar.core.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日志配置属性
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogProperties {
    /**
     * 日志等级，默认为 'info'
     */
    @Builder.Default
    private String logLevel = "info";
}

