package com.brgroup.cybotstar.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 高性能队列实现
 * 使用 head 索引避免 remove(0) 的 O(n) 复杂度，提升性能
 * 
 * 线程安全说明：
 * - items 使用 Collections.synchronizedList 保证线程安全
 * - head 字段使用 volatile 确保多线程环境下的可见性
 * - add() 方法通过 synchronizedList 自动同步，线程安全
 *
 * 内存管理：
 * - 当 head 超过阈值且超过队列大小的一半时，自动清理已消费的元素
 * - 默认清理阈值为 100，清理触发比例为 0.5（即 head > items.size() / 2）
 *
 * @author zhiyuan.xi
 */
public class FastQueue<T> {
    /**
     * 同步列表，保证线程安全
     * 所有对 items 的访问都通过同步机制保护
     */
    public final List<T> items = Collections.synchronizedList(new ArrayList<>());

    /**
     * 队列头索引，O(1) 出队的关键
     * 使用 volatile 确保多线程环境下的可见性
     */
    private volatile int head = 0;

    /**
     * 清理阈值：当 head 超过此值时，才考虑清理
     * 避免频繁清理小队列，提升性能
     */
    private static final int CLEANUP_THRESHOLD = 100;

    /**
     * 清理触发比例：当 head 超过队列大小 * 此比例时，触发清理
     * 例如：0.5 表示当 head > items.size() / 2 时清理
     */
    private static final double CLEANUP_RATIO = 0.5;

    /**
     * 入队
     * 线程安全：通过 Collections.synchronizedList 自动同步
     */
    public void add(T item) {
        items.add(item);
    }

    /**
     * 出队
     * O(1) 时间复杂度，通过 head 索引实现
     */
    public T remove() {
        synchronized (items) {
            if (head >= items.size()) {
                return null;
            }
            T item = items.get(head);
            head++;

            // 智能清理策略：当 head 超过阈值且超过队列大小的一定比例时，清理已消费的元素
            // 这样可以保持 O(1) 性能，同时控制内存占用
            // 使用常量配置，便于后续优化和调整
            if (head > CLEANUP_THRESHOLD && head > items.size() * CLEANUP_RATIO) {
                items.subList(0, head).clear();
                head = 0;
            }

            return item;
        }
    }

    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        synchronized (items) {
            return head >= items.size();
        }
    }

    /**
     * 清空队列
     */
    public void clear() {
        synchronized (items) {
            items.clear();
            head = 0;
        }
    }

    /**
     * 通知所有等待者
     */
    public void notifyWaiters() {
        synchronized (items) {
            items.notifyAll();
        }
    }
}
