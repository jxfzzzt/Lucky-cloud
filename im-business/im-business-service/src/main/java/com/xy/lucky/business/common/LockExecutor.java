package com.xy.lucky.business.common;

import com.xy.lucky.business.exception.BusinessResultCode;
import com.xy.lucky.business.exception.MessageException;
import com.xy.lucky.general.response.service.I18nService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁执行器
 * <p>
 * 统一封装分布式锁的获取、执行和释放逻辑，提供简洁的 API
 * </p>
 *
 * @author xy
 */
@Slf4j
@Component
public class LockExecutor {

    /**
     * 默认锁等待时间（秒）
     */
    private static final long DEFAULT_WAIT_TIME = 5L;
    /**
     * 默认锁持有时间（秒）
     */
    private static final long DEFAULT_LEASE_TIME = 10L;

    private final RedissonClient redissonClient;

    public LockExecutor(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 在分布式锁保护下执行操作（使用默认超时时间）
     *
     * @param lockKey 锁的 key
     * @param action  要执行的操作
     * @param <T>     返回值类型
     * @return 操作的返回值
     */
    public <T> T execute(String lockKey, Supplier<T> action) {
        return execute(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, action);
    }

    /**
     * 在分布式锁保护下执行操作（自定义超时时间）
     *
     * @param lockKey   锁的 key
     * @param waitTime  等待锁的最大时间（秒）
     * @param leaseTime 持有锁的最大时间（秒）
     * @param action    要执行的操作
     * @param <T>       返回值类型
     * @return 操作的返回值
     */
    public <T> T execute(String lockKey, long waitTime, long leaseTime, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn(I18nService.getMessage("log.lock.acquire_failed",
                        new Object[]{lockKey}));
                throw new MessageException(BusinessResultCode.LOCK_BUSY_RETRY);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(I18nService.getMessage("log.lock.interrupted",
                    new Object[]{lockKey}), e);
            throw new MessageException(BusinessResultCode.LOCK_OPERATION_INTERRUPTED);
        } finally {
            safeUnlock(lock, acquired);
        }
    }

    /**
     * 在分布式锁保护下执行操作（无返回值）
     *
     * @param lockKey 锁的 key
     * @param action  要执行的操作
     */
    public void execute(String lockKey, Runnable action) {
        execute(lockKey, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 在分布式锁保护下执行可能抛出异常的操作
     *
     * @param lockKey 锁的 key
     * @param action  要执行的操作
     * @param <T>     返回值类型
     * @return 操作的返回值
     */
    public <T> T executeWithException(String lockKey, ThrowingSupplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS);
            if (!acquired) {
                throw new MessageException(BusinessResultCode.LOCK_BUSY_RETRY);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageException(BusinessResultCode.LOCK_OPERATION_INTERRUPTED);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            safeUnlock(lock, acquired);
        }
    }

    /**
     * 安全释放锁
     */
    private void safeUnlock(RLock lock, boolean acquired) {
        if (acquired && lock.isHeldByCurrentThread()) {
            try {
                lock.unlock();
            } catch (Exception e) {
                log.warn(I18nService.getMessage("log.lock.release_failed"), e);
            }
        }
    }

    /**
     * 可抛出异常的 Supplier
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}

