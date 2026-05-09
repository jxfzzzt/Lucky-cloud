package com.xy.lucky.auth.service.impl;

import com.xy.lucky.auth.config.SmsCodeProperties;
import com.xy.lucky.auth.service.RateLimitService;
import com.xy.lucky.general.response.domain.ResultCode;
import com.xy.lucky.security.exception.AuthenticationFailException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

    private static final String SMS_RATE_LIMIT_PHONE_KEY_PREFIX = "im:auth:rate_limit:sms:phone:";
    private static final String SMS_RATE_LIMIT_IP_KEY_PREFIX = "im:auth:rate_limit:sms:ip:";
    private static final String SMS_RATE_LIMIT_DEVICE_KEY_PREFIX = "im:auth:rate_limit:sms:device:";
    private static final String SMS_BLOCK_PHONE_KEY_PREFIX = "im:auth:block:sms:phone:";
    private static final String SMS_BLOCK_IP_KEY_PREFIX = "im:auth:block:sms:ip:";
    private static final String SMS_BLOCK_DEVICE_KEY_PREFIX = "im:auth:block:sms:device:";

    private final StringRedisTemplate stringRedisTemplate;
    private final SmsCodeProperties smsCodeProperties;

    @Override
    public boolean allowSmsSend(String phone, String clientIp, String deviceId) {
        SmsCodeProperties.RateLimit cfg = smsCodeProperties.getRateLimit();

        String phoneKey = SMS_RATE_LIMIT_PHONE_KEY_PREFIX + DigestUtils.sha256Hex(Objects.requireNonNullElse(phone, ""));
        String ipKey = SMS_RATE_LIMIT_IP_KEY_PREFIX + Objects.requireNonNullElse(clientIp, "");
        String deviceKey = SMS_RATE_LIMIT_DEVICE_KEY_PREFIX + DigestUtils.sha256Hex(Objects.requireNonNullElse(deviceId, ""));

        if (isBlocked(phone, clientIp, deviceId)) {
            throw new AuthenticationFailException(ResultCode.TOO_MANY_REQUESTS);
        }

        boolean phoneOk = withinLimit(phoneKey, cfg.getPhoneLimit(), cfg.getWindow());
        boolean ipOk = withinLimit(ipKey, cfg.getIpLimit(), cfg.getWindow());
        boolean deviceOk = withinLimit(deviceKey, cfg.getDeviceLimit(), cfg.getWindow());

        if (phoneOk && ipOk && deviceOk) {
            return true;
        }

        blockIfNeeded(phone, clientIp, deviceId, cfg.getBlockDuration());
        throw new AuthenticationFailException(ResultCode.TOO_MANY_REQUESTS);
    }

    private boolean isBlocked(String phone, String clientIp, String deviceId) {
        if (StringUtils.hasText(phone)) {
            String key = SMS_BLOCK_PHONE_KEY_PREFIX + DigestUtils.sha256Hex(phone);
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) return true;
        }
        if (StringUtils.hasText(clientIp)) {
            String key = SMS_BLOCK_IP_KEY_PREFIX + clientIp;
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) return true;
        }
        if (StringUtils.hasText(deviceId)) {
            String key = SMS_BLOCK_DEVICE_KEY_PREFIX + DigestUtils.sha256Hex(deviceId);
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        }
        return false;
    }

    private boolean withinLimit(String key, int limit, Duration window) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        long c = Objects.requireNonNullElse(count, 0L);
        if (c == 1L) {
            stringRedisTemplate.expire(key, window);
        }
        return c <= limit;
    }

    private void blockIfNeeded(String phone, String clientIp, String deviceId, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) return;
        if (StringUtils.hasText(phone)) {
            String key = SMS_BLOCK_PHONE_KEY_PREFIX + DigestUtils.sha256Hex(phone);
            stringRedisTemplate.opsForValue().set(key, "1", duration);
        }
        if (StringUtils.hasText(clientIp)) {
            String key = SMS_BLOCK_IP_KEY_PREFIX + clientIp;
            stringRedisTemplate.opsForValue().set(key, "1", duration);
        }
        if (StringUtils.hasText(deviceId)) {
            String key = SMS_BLOCK_DEVICE_KEY_PREFIX + DigestUtils.sha256Hex(deviceId);
            stringRedisTemplate.opsForValue().set(key, "1", duration);
        }
    }
}
