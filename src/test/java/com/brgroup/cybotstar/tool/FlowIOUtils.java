package com.brgroup.cybotstar.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * Flow IO 工具类
 * 提供输入输出工具函数，用于 Flow 示例
 *
 * @author zhiyuan.xi
 */
public class FlowIOUtils {

    private static BufferedReader sharedReader = null;

    /**
     * 获取共享的 BufferedReader 实例
     */
    private static BufferedReader getSharedReader() {
        if (sharedReader == null) {
            sharedReader = new BufferedReader(new InputStreamReader(System.in));
        }
        return sharedReader;
    }

    /**
     * 关闭共享的 BufferedReader 实例
     */
    public static void closeSharedReader() {
        if (sharedReader != null) {
            try {
                sharedReader.close();
            } catch (IOException e) {
                // 忽略关闭错误
            }
            sharedReader = null;
        }
    }

    /**
     * 创建控制台输入生产者
     * 从标准输入读取用户输入
     *
     * @return 输入字符串
     */
    public static String readInput() {
        try {
            System.out.print("> ");
            BufferedReader reader = getSharedReader();
            return reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException("读取输入失败", e);
        }
    }

    /**
     * 创建控制台输出消费者
     *
     * @param prefix 输出前缀
     * @return 消费者函数
     */
    public static Consumer<String> createConsoleConsumer(String prefix) {
        return text -> System.out.print(prefix + text);
    }

    /**
     * 流式输出消费者
     */
    public static class StreamConsumer {
        private final String prefix;
        private boolean isFirst = true;

        public StreamConsumer(String prefix) {
            this.prefix = prefix;
        }

        /**
         * 输出流式片段
         */
        public void chunk(String text) {
            if (isFirst) {
                System.out.print(prefix);
                isFirst = false;
            }
            System.out.print(text);
        }

        /**
         * 完成输出
         */
        public void complete() {
            if (!isFirst) {
                System.out.println();
                isFirst = true;
            }
        }

        /**
         * 输出错误
         */
        public void error(Exception error) {
            System.out.println("\n❌ 错误: " + error.getMessage());
            isFirst = true;
        }
    }

    /**
     * 创建流式终端输出消费者
     *
     * @param prefix 输出前缀
     * @return 流式消费者
     */
    public static StreamConsumer createStreamConsumer(String prefix) {
        return new StreamConsumer(prefix);
    }
}

