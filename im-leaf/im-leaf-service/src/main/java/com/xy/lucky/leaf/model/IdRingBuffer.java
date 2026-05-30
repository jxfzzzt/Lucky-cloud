package com.xy.lucky.leaf.model;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单的环形缓冲区，用于缓存ID
 * 使用无锁队列和原子计数保证线程安全，减少锁竞争
 *
 * @param <E> 元素类型
 */
public class IdRingBuffer<E> {

    private final int capacity;
    private final ConcurrentLinkedQueue<E> queue;
    private final AtomicInteger size;

    /**
     * 构造函数
     *
     * @param capacity 缓冲区容量，必须是2的幂
     */
    public IdRingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than 0");
        }
        this.capacity = capacity;
        this.queue = new ConcurrentLinkedQueue<>();
        this.size = new AtomicInteger(0);
    }

    /**
     * 添加元素到缓冲区尾部
     *
     * @param e 要添加的元素
     * @throws IllegalStateException 当缓冲区已满时抛出
     */
    public void put(E e) {
        if (e == null) {
            throw new IllegalArgumentException("element must not be null");
        }
        while (true) {
            int current = size.get();
            if (current >= capacity) {
                throw new IllegalStateException("RingBuffer is full");
            }
            if (size.compareAndSet(current, current + 1)) {
                queue.offer(e);
                return;
            }
        }
    }

    /**
     * 从缓冲区头部获取元素
     *
     * @return 从缓冲区头部获取的元素
     * @throws IllegalStateException 当缓冲区为空时抛出
     */
    public E take() {
        while (true) {
            E value = queue.poll();
            if (value != null) {
                decrementSize();
                return value;
            }
            if (size.get() == 0) {
                throw new IllegalStateException("RingBuffer is empty");
            }
            Thread.onSpinWait();
        }
    }

    private void decrementSize() {
        while (true) {
            int current = size.get();
            if (current <= 0) {
                return;
            }
            if (size.compareAndSet(current, current - 1)) {
                return;
            }
        }
    }

    /**
     * 检查缓冲区是否已满
     *
     * @return 如果缓冲区已满则返回true，否则返回false
     */
    public boolean isFull() {
        return size.get() == capacity;
    }

    /**
     * 检查缓冲区是否为空
     *
     * @return 如果缓冲区为空则返回true，否则返回false
     */
    public boolean isEmpty() {
        return size.get() == 0;
    }

    /**
     * 获取当前缓冲区中元素的数量
     *
     * @return 当前缓冲区中元素的数量
     */
    public int size() {
        return size.get();
    }
}
