package com.brgroup.cybotstar.core.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP 配置属性
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpProperties {
    /**
     * HTTP API URL（用于 HTTP 请求）
     * 默认值：https://www.cybotstar.cn/openapi/v2/
     */
    @Builder.Default
    private String url = "https://www.cybotstar.cn/openapi/v2/";

    /**
     * 连接超时时间（毫秒），默认 30000
     */
    @Builder.Default
    private Integer connectTimeout = 30000;

    /**
     * 读取超时时间（毫秒），默认 30000
     */
    @Builder.Default
    private Integer readTimeout = 30000;

    /**
     * 写入超时时间（毫秒），默认 30000
     */
    @Builder.Default
    private Integer writeTimeout = 30000;
}
