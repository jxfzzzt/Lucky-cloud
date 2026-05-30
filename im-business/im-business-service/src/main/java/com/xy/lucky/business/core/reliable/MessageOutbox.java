package com.xy.lucky.business.core.reliable;

import com.xy.lucky.business.core.reliable.domain.OutboxMessage;
import com.xy.lucky.business.core.reliable.domain.OutboxStatus;
import com.xy.lucky.domain.po.IMOutboxPo;
import com.xy.lucky.general.response.service.I18nService;
import com.xy.lucky.rpc.api.database.outbox.IMOutboxDubboService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * 消息发件箱 - 保证消息可靠投递
 * <p>
 * 核心机制：
 * 1. 本地写入 + 异步持久化（提升性能）
 * 2. 发送确认 + 状态追踪
 * 3. 定时重试失败消息
 * 4. 死信处理
 * <p>
 * 解决消息丢失问题的关键组件
 */
@Slf4j
@Component
public class MessageOutbox {

    /**
     * 待确认消息映射：messageId -> OutboxMessage
     */
    private final Map<String, OutboxMessage> pendingMessages = new ConcurrentHashMap<>();
    /**
     * 异步持久化队列
     */
    private final Queue<OutboxMessage> persistQueue = new ConcurrentLinkedQueue<>();
    /**
     * 重试队列
     */
    private final DelayQueue<DelayedMessage> retryQueue = new DelayQueue<>();
    /**
     * 统计计数器
     */
    private final LongAdder sentCount = new LongAdder();
    private final LongAdder confirmedCount = new LongAdder();
    private final LongAdder failedCount = new LongAdder();
    private final LongAdder retryCount = new LongAdder();
    /**
     * 运行状态
     */
    private final AtomicBoolean running = new AtomicBoolean(true);
    @DubboReference
    private IMOutboxDubboService outboxDubboService;
    @Resource
    private RabbitTemplate rabbitTemplate;
    /**
     * 持久化线程
     */
    private ExecutorService persistExecutor;

    /**
     * 重试线程
     */
    private ExecutorService retryExecutor;

    @Value("${message.outbox.max-retry:3}")
    private int maxRetry = 3;

    @Value("${message.outbox.retry-delay-ms:5000}")
    private long retryDelayMs = 5000L;

    @Value("${message.outbox.confirm-timeout-ms:10000}")
    private long confirmTimeoutMs = 10000L;

    @PostConstruct
    public void init() {
        // 启动持久化线程
        persistExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "outbox-persist");
            t.setDaemon(true);
            return t;
        });
        persistExecutor.submit(this::persistLoop);

        // 启动重试线程
        retryExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "outbox-retry");
            t.setDaemon(true);
            return t;
        });
        retryExecutor.submit(this::retryLoop);

        log.info(I18nService.getMessage("log.outbox.init",
                new Object[]{maxRetry, retryDelayMs}));
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (persistExecutor != null) {
            persistExecutor.shutdownNow();
        }
        if (retryExecutor != null) {
            retryExecutor.shutdownNow();
        }
        // 持久化剩余消息
        flushPending();
    }

    /**
     * 发送消息（带可靠性保证）
     *
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param payload    消息内容
     * @param messageId  消息 ID
     * @return 发送是否成功提交
     */
    public boolean send(String exchange, String routingKey, String payload, String messageId) {
        OutboxMessage outbox = new OutboxMessage()
                .setMessageId(messageId)
                .setExchange(exchange)
                .setRoutingKey(routingKey)
                .setPayload(payload)
                .setStatus(OutboxStatus.PENDING)
                .setRetryCount(0)
                .setCreateTime(System.currentTimeMillis());

        // 加入待确认映射
        pendingMessages.put(messageId, outbox);

        // 异步持久化
        persistQueue.offer(outbox);

        // 发送到 MQ
        try {
            CorrelationData correlationData = new CorrelationData(messageId);
            rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlationData);
            sentCount.increment();
            log.debug(I18nService.getMessage("log.outbox.send_success",
                    new Object[]{messageId, routingKey}));
            return true;
        } catch (Exception e) {
            log.error(I18nService.getMessage("log.outbox.send_failed",
                    new Object[]{messageId, e.getMessage()}));
            // 加入重试队列
            scheduleRetry(outbox);
            return false;
        }
    }

    /**
     * 处理发送确认回调
     */
    public void handleConfirm(String messageId, boolean ack, String cause) {
        OutboxMessage outbox = pendingMessages.remove(messageId);
        if (outbox == null) {
            log.debug(I18nService.getMessage("log.outbox.confirm_not_found",
                    new Object[]{messageId}));
            return;
        }

        if (ack) {
            outbox.setStatus(OutboxStatus.CONFIRMED);
            confirmedCount.increment();
            log.debug(I18nService.getMessage("log.outbox.confirm_success",
                    new Object[]{messageId}));

            // 异步更新数据库状态
            updateOutboxStatusAsync(outbox);
        } else {
            log.warn(I18nService.getMessage("log.outbox.confirm_failed",
                    new Object[]{messageId, cause}));
            outbox.setStatus(OutboxStatus.FAILED);
            outbox.setErrorMessage(cause);
            failedCount.increment();

            // 加入重试队列
            scheduleRetry(outbox);
        }
    }

    /**
     * 处理消息返回回调（路由失败）
     */
    public void handleReturn(String messageId, String replyText) {
        OutboxMessage outbox = pendingMessages.remove(messageId);
        if (outbox != null) {
            log.warn(I18nService.getMessage("log.outbox.route_failed",
                    new Object[]{messageId, replyText}));
            outbox.setStatus(OutboxStatus.RETURNED);
            outbox.setErrorMessage(replyText);
            failedCount.increment();

            // 路由失败通常不重试，直接标记失败
            updateOutboxStatusAsync(outbox);
        }
    }

    /**
     * 调度重试
     */
    private void scheduleRetry(OutboxMessage outbox) {
        if (outbox.getRetryCount() >= maxRetry) {
            log.error(I18nService.getMessage("log.outbox.dead_letter",
                    new Object[]{outbox.getMessageId()}));
            outbox.setStatus(OutboxStatus.DEAD);
            updateOutboxStatusAsync(outbox);
            return;
        }

        outbox.setRetryCount(outbox.getRetryCount() + 1);
        outbox.setStatus(OutboxStatus.RETRY);

        // 指数退避
        long delay = retryDelayMs * (1L << (outbox.getRetryCount() - 1));
        retryQueue.offer(new DelayedMessage(outbox, delay));

        log.info(I18nService.getMessage("log.outbox.requeue",
                new Object[]{outbox.getMessageId(), outbox.getRetryCount(), delay}));
    }

    /**
     * 持久化循环
     */
    private void persistLoop() {
        while (running.get()) {
            try {
                OutboxMessage msg = persistQueue.poll();
                if (msg != null) {
                    persistToDatabase(msg);
                } else {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error(I18nService.getMessage("log.outbox.persist_exception",
                        new Object[]{e.getMessage()}));
            }
        }
    }

    /**
     * 重试循环
     */
    private void retryLoop() {
        while (running.get()) {
            try {
                DelayedMessage delayed = retryQueue.poll(1, TimeUnit.SECONDS);
                if (delayed != null) {
                    retryMessage(delayed.outbox);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error(I18nService.getMessage("log.outbox.retry_exception",
                        new Object[]{e.getMessage()}));
            }
        }
    }

    /**
     * 重试发送
     */
    private void retryMessage(OutboxMessage outbox) {
        retryCount.increment();
        log.info(I18nService.getMessage("log.outbox.retry",
                new Object[]{outbox.getMessageId(), outbox.getRetryCount()}));

        pendingMessages.put(outbox.getMessageId(), outbox);

        try {
            CorrelationData correlationData = new CorrelationData(outbox.getMessageId());
            rabbitTemplate.convertAndSend(
                    outbox.getExchange(),
                    outbox.getRoutingKey(),
                    outbox.getPayload(),
                    correlationData
            );
        } catch (Exception e) {
            log.error(I18nService.getMessage("log.outbox.retry_failed",
                    new Object[]{outbox.getMessageId(), e.getMessage()}));
            scheduleRetry(outbox);
        }
    }

    /**
     * 持久化到数据库
     */
    private void persistToDatabase(OutboxMessage msg) {
        try {
            IMOutboxPo po = new IMOutboxPo();
            po.setMessageId(msg.getMessageId());
            po.setPayload(msg.getPayload());
            po.setExchange(msg.getExchange());
            po.setRoutingKey(msg.getRoutingKey());
            po.setStatus(String.valueOf(msg.getStatus().getCode()));
            // outboxDubboService.insert(po);
        } catch (Exception e) {
            log.error(I18nService.getMessage("log.outbox.persist_failed",
                    new Object[]{msg.getMessageId(), e.getMessage()}));
        }
    }

    /**
     * 异步更新状态
     */
    private void updateOutboxStatusAsync(OutboxMessage msg) {
        CompletableFuture.runAsync(() -> {
            try {
                // outboxDubboService.modifyStatus(null, String.valueOf(msg.getStatus().getCode()), msg.getRetryCount());
            } catch (Exception e) {
                log.error(I18nService.getMessage("log.outbox.update_status_failed",
                        new Object[]{msg.getMessageId()}), e);
            }
        });
    }

    /**
     * 刷新待处理消息
     */
    private void flushPending() {
        log.info(I18nService.getMessage("log.outbox.flush",
                new Object[]{pendingMessages.size(), persistQueue.size(), retryQueue.size()}));

        // 持久化所有待处理消息
        OutboxMessage msg;
        while ((msg = persistQueue.poll()) != null) {
            persistToDatabase(msg);
        }
    }

    /**
     * 定时检查超时未确认的消息
     */
    @Scheduled(fixedDelay = 30000)
    public void checkTimeoutMessages() {
        long now = System.currentTimeMillis();
        pendingMessages.forEach((messageId, outbox) -> {
            if (now - outbox.getCreateTime() > confirmTimeoutMs) {
                log.warn(I18nService.getMessage("log.outbox.confirm_timeout",
                        new Object[]{messageId}));
                pendingMessages.remove(messageId);
                scheduleRetry(outbox);
            }
        });
    }

    /**
     * 定时扫描数据库中的 pending 消息进行重试
     */
    @Scheduled(fixedDelay = 60000)
    public void scanPendingFromDatabase() {
        try {
            List<IMOutboxPo> pendingList = outboxDubboService.queryList();
            if (pendingList != null && !pendingList.isEmpty()) {
                log.info(I18nService.getMessage("log.outbox.scan_count",
                        new Object[]{pendingList.size()}));
                for (IMOutboxPo po : pendingList) {
                    OutboxMessage msg = new OutboxMessage()
                            .setMessageId(po.getMessageId())
                            .setExchange(po.getExchange())
                            .setRoutingKey(po.getRoutingKey())
                            .setPayload(po.getPayload().toString())
                            .setStatus(OutboxStatus.RETRY)
                            .setRetryCount(0)
                            .setCreateTime(System.currentTimeMillis());

                    if (!pendingMessages.containsKey(po.getMessageId())) {
                        scheduleRetry(msg);
                    }
                }
            }
        } catch (Exception e) {
            log.error(I18nService.getMessage("log.outbox.scan_error",
                    new Object[]{e.getMessage()}));
        }
    }

    /**
     * 获取统计信息
     */
    public OutboxStats getStats() {
        return new OutboxStats(
                sentCount.sum(),
                confirmedCount.sum(),
                failedCount.sum(),
                retryCount.sum(),
                pendingMessages.size(),
                retryQueue.size()
        );
    }

    /**
     * 统计信息
     */
    public record OutboxStats(
            long sent,
            long confirmed,
            long failed,
            long retried,
            int pending,
            int retryQueueSize
    ) {
        public double confirmRate() {
            return sent == 0 ? 100 : (double) confirmed / sent * 100;
        }
    }

    /**
     * 延迟消息包装
     */
    @Data
    private static class DelayedMessage implements Delayed {

        private OutboxMessage outbox;
        private long delayMs;
        private long triggerTime;

        public DelayedMessage(OutboxMessage outbox, long delayMs) {
            this.outbox = outbox;
            this.delayMs = delayMs;
            this.triggerTime = System.currentTimeMillis() + delayMs;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(triggerTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(this.getDelay(TimeUnit.MILLISECONDS),
                    o.getDelay(TimeUnit.MILLISECONDS));
        }
    }
}

