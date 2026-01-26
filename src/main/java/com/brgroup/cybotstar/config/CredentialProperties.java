package com.brgroup.cybotstar.config;

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
}

