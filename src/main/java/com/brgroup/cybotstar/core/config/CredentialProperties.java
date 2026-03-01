package com.brgroup.cybotstar.core.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 凭证配置
 *
 * @author zhiyuan.xi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialProperties {
    /**
     * 机器人 Key (cybertron_robot_key)
     */
    private String robotKey;

    /**
     * 机器人 Token (cybertron_robot_token)
     */
    private String robotToken;

    /**
     * 用户名
     */
    private String username;

    /**
     * 脱敏 toString 方法，防止凭证泄露到日志
     */
    @Override
    public String toString() {
        return "CredentialProperties{" +
                "robotKey='" + maskSensitive(robotKey) + '\'' +
                ", robotToken='" + maskSensitive(robotToken) + '\'' +
                ", username='" + username + '\'' +
                '}';
    }

    /**
     * 脱敏敏感信息
     */
    private static String maskSensitive(String value) {
        if (value == null || value.isEmpty()) {
            return "****";
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}

