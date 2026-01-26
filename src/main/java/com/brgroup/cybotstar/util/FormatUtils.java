package com.brgroup.cybotstar.util;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 格式化工具类
 * 提供模板字符串变量替换和格式化功能
 *
 * @author zhiyuan.xi
 */
public class FormatUtils {

    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    /**
     * 替换模板字符串中的变量
     * 将模板字符串中的 {{variable}} 格式的变量替换为对应的值
     *
     * @param template 模板字符串
     * @param params   变量参数对象
     * @return 替换后的字符串
     */
    @Nullable
    public static String replaceTemplateVariables(@Nullable String template, @Nullable Map<String, String> params) {
        if (template == null || params == null || params.isEmpty()) {
            return template;
        }

        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = params.get(key);
            if (replacement != null) {
                matcher.appendReplacement(result, replacement);
            } else {
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 格式化会话 ID（用于日志显示）
     *
     * @param sessionId 会话 ID
     * @return 格式化后的会话 ID（空字符串显示为 'default'）
     */
    @NonNull
    public static String formatSessionId(@Nullable String sessionId) {
        return (sessionId == null || sessionId.isEmpty()) ? Constants.DEFAULT_SESSION_DISPLAY_NAME : sessionId;
    }
}

