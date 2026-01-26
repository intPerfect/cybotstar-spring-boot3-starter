package com.brgroup.cybotstar.tool;

/**
 * 流式渲染器
 * 用于渲染流式输出内容，支持分别渲染 reasoning 和 answer
 *
 * @author zhiyuan.xi
 */
public class StreamRenderer {
    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";
    private static final String GRAY = "\u001B[90m";
    private static final String YELLOW = "\u001B[33m";

    private boolean isStreaming = false;
    private boolean isReasoning = false;
    private final StringBuilder buffer = new StringBuilder();
    private final StringBuilder reasoningBuffer = new StringBuilder();

    /**
     * 开始 Reasoning 输出
     */
    public void startReasoning() {
        if (!isReasoning) {
            System.out.print("\n" + YELLOW + "[Reasoning]: " + RESET);
            reasoningBuffer.setLength(0);
            isReasoning = true;
        }
    }

    /**
     * 追加 Reasoning 内容
     *
     * @param text 文本内容
     */
    public void appendReasoning(String text) {
        if (isReasoning) {
            System.out.print(text);
            reasoningBuffer.append(text);
        }
    }

    /**
     * 完成 Reasoning 输出，切换到 Answer
     */
    public void finishReasoning() {
        if (isReasoning) {
            System.out.print(RESET + "\n");
            if (reasoningBuffer.length() > 0) {
                System.out.println(GRAY + "📊 [Reasoning: " + reasoningBuffer.length() + " 字符]\n" + RESET);
            }
            reasoningBuffer.setLength(0);
            isReasoning = false;
        }
    }

    /**
     * 开始新的输出（打印前缀）
     *
     * @param prefix 输出前缀，默认为 "🤖 Answer: "
     */
    public void start(String prefix) {
        // 如果还在输出 reasoning，先完成它
        if (isReasoning) {
            finishReasoning();
        }
        System.out.print(GREEN + prefix);
        buffer.setLength(0);
        isStreaming = true;
    }

    /**
     * 开始新的输出（使用默认前缀）
     */
    public void start() {
        start("🤖 Answer: ");
    }

    /**
     * 追加文本内容
     *
     * @param text 文本内容
     */
    public void append(String text) {
        if (isStreaming) {
            System.out.print(text);
            buffer.append(text);
        }
    }

    /**
     * 完成输出（换行并显示统计）
     */
    public void finish() {
        // 如果还在输出 reasoning，先完成它
        if (isReasoning) {
            finishReasoning();
        }
        if (isStreaming) {
            System.out.print(RESET + "\n");
            System.out.println(GRAY + "📊 [Answer: " + buffer.length() + " 字符]\n" + RESET);
            buffer.setLength(0);
            isStreaming = false;
        }
    }

    /**
     * 获取缓冲区内容
     *
     * @return 缓冲区内容
     */
    public String getBuffer() {
        return buffer.toString();
    }

    /**
     * 获取 Reasoning 缓冲区内容
     *
     * @return Reasoning 缓冲区内容
     */
    public String getReasoningBuffer() {
        return reasoningBuffer.toString();
    }

    /**
     * 检查是否正在流式输出 Answer
     *
     * @return 是否正在流式输出
     */
    public boolean isStreaming() {
        return isStreaming;
    }

    /**
     * 检查是否正在输出 Reasoning
     *
     * @return 是否正在输出 Reasoning
     */
    public boolean isReasoning() {
        return isReasoning;
    }

    /**
     * 清空缓冲区
     */
    public void clear() {
        buffer.setLength(0);
        reasoningBuffer.setLength(0);
    }
}

