package com.xy.lucky.auth.security.helper;

import com.xy.lucky.domain.po.ImUserPo;
import com.xy.lucky.rpc.api.database.user.ImUserDubboService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录态短期用户缓存，减少高频登录时对用户服务的同步查询压力。
 */
@Slf4j
@Component
public class AuthUserCacheHelper {

    @DubboReference
    private ImUserDubboService imUserDubboService;

    @Value("${auth.user-cache-seconds:30}")
    private long cacheSeconds;

    private long ttlMs;

    private final ConcurrentHashMap<String, CachedUser> userIdCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedUser> mobileCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        ttlMs = Math.max(1L, cacheSeconds) * 1000L;
    }

    public AuthUserSnapshot requireByUserId(String userId) {
        return Optional.ofNullable(getByUserId(userId))
                .orElse(null);
    }

    public AuthUserSnapshot requireByMobile(String mobile) {
        return Optional.ofNullable(getByMobile(mobile))
                .orElse(null);
    }

    public void evictByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        CachedUser cached = userIdCache.remove(userId);
        if (cached != null && StringUtils.hasText(cached.snapshot.mobile())) {
            mobileCache.remove(cached.snapshot.mobile());
        }
    }

    private AuthUserSnapshot getByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        long now = System.currentTimeMillis();
        CachedUser cached = userIdCache.get(userId);
        if (cached != null && cached.expireAtMs > now) {
            return cached.snapshot;
        }

        ImUserPo user = imUserDubboService.queryOne(userId);
        if (user == null) {
            userIdCache.remove(userId);
            return null;
        }

        AuthUserSnapshot snapshot = AuthUserSnapshot.from(user);
        cache(snapshot, now);
        return snapshot;
    }

    private AuthUserSnapshot getByMobile(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return null;
        }
        long now = System.currentTimeMillis();
        CachedUser cached = mobileCache.get(mobile);
        if (cached != null && cached.expireAtMs > now) {
            return cached.snapshot;
        }

        ImUserPo user = imUserDubboService.queryOneByMobile(mobile);
        if (user == null) {
            mobileCache.remove(mobile);
            return null;
        }

        AuthUserSnapshot snapshot = AuthUserSnapshot.from(user);
        cache(snapshot, now);
        return snapshot;
    }

    private void cache(AuthUserSnapshot snapshot, long now) {
        CachedUser cached = new CachedUser(snapshot, now + ttlMs);
        userIdCache.put(snapshot.userId(), cached);
        if (StringUtils.hasText(snapshot.mobile())) {
            mobileCache.put(snapshot.mobile(), cached);
        }
    }

    private record CachedUser(AuthUserSnapshot snapshot, long expireAtMs) {
    }

    public record AuthUserSnapshot(String userId, String password, String mobile) {
        static AuthUserSnapshot from(ImUserPo user) {
            return new AuthUserSnapshot(user.getUserId(), user.getPassword(), user.getMobile());
        }
    }
}
