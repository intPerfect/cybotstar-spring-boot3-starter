package com.brgroup.cybotstar.tool;

import com.brgroup.cybotstar.flow.FlowClient;
import com.brgroup.cybotstar.flow.model.FlowState;

/**
 * 颜色日志工具类
 * 提供统一的控制台颜色输出
 *
 * @author zhiyuan.xi
 */
public class ColorPrinter {
    private static final String RESET = "\u001B[0m";
    private static final String BRIGHT = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String GRAY = "\u001B[90m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String RED = "\u001B[31m";

    /**
     * 处理消息，如果开头有 \n 则先打印换行
     */
    private static String processMessage(String message) {
        if (message == null) {
            return "";
        }
        int index = 0;
        while (index < message.length() && message.charAt(index) == '\n') {
            System.out.println();
            index++;
        }
        return message.substring(index);
    }

    /**
     * 成功消息（绿色）
     */
    public static void success(String message) {
        String processed = processMessage(message);
        System.out.println(GREEN + "✅ " + processed + RESET);
    }

    /**
     * 信息消息（灰色）
     */
    public static void info(String message) {
        String processed = processMessage(message);
        System.out.println(GRAY + "ℹ️ " + processed + RESET);
    }

    /**
     * 问题（青色）
     */
    public static void question(String message) {
        String processed = processMessage(message);
        System.out.println(CYAN + "💻 " + processed + RESET);
    }

    /**
     * 标题（亮白色）
     */
    public static void title(String message) {
        String processed = processMessage(message);
        System.out.println("\n" + BRIGHT + WHITE + processed + RESET);
    }

    /**
     * 分隔线（白色）
     */
    public static void separator(char ch, int length) {
        System.out.println(WHITE + String.valueOf(ch).repeat(length) + RESET);
    }

    /**
     * 打印 Flow 状态（灰色）
     */
    public static void printState(FlowClient flow) {
        System.out.println(GRAY + "(FlowState: " + flow.getState() + ")" + RESET);
    }

    /**
     * 打印 Flow 状态（灰色）
     */
    public static void printState(FlowState state) {
        System.out.println(GRAY + "(FlowState: " + state + ")" + RESET);
    }

    /**
     * 用户输入消息（青色）
     */
    public static void userInput(String input) {
        String processed = processMessage(input);
        System.out.println(CYAN + "👤 User: " + processed + RESET);
    }

    /**
     * 错误消息（红色）
     */
    public static void error(String message) {
        String processed = processMessage(message);
        System.out.println(RED + "❌ " + processed + RESET);
    }

    /**
     * 错误消息（红色），带异常信息
     */
    public static void error(String message, Throwable throwable) {
        String processed = processMessage(message);
        System.out.println(RED + "❌ " + processed + RESET);
        if (throwable != null) {
            System.out.println(RED + "   异常: " + throwable.getMessage() + RESET);
            throwable.printStackTrace();
        }
    }

    /**
     * 调试消息（灰色）
     */
    public static void debug(String message) {
        String processed = processMessage(message);
        System.out.println(GRAY + "🔍 " + processed + RESET);
    }

    /**
     * 节点进入消息（灰色）
     */
    public static void nodeEnter(String message) {
        String processed = processMessage(message);
        System.out.println(GRAY + "📌 " + processed + RESET);
    }

    /**
     * 跳转消息（灰色）
     */
    public static void jump(String message) {
        String processed = processMessage(message);
        System.out.println(GRAY + "🔄 " + processed + RESET);
    }
}
