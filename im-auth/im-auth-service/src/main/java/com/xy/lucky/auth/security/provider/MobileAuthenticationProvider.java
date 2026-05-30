package com.xy.lucky.auth.security.provider;

import com.xy.lucky.auth.security.domain.AuthRequestContext;
import com.xy.lucky.auth.security.helper.AuthUserCacheHelper;
import com.xy.lucky.auth.security.helper.CryptoHelper;
import com.xy.lucky.auth.security.token.MobileAuthenticationToken;
import com.xy.lucky.auth.service.SmsCodeService;
import com.xy.lucky.general.response.domain.ResultCode;
import com.xy.lucky.security.exception.AuthenticationFailException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 手机验证码认证提供者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MobileAuthenticationProvider implements AuthenticationProvider {

    private final AuthUserCacheHelper authUserCacheHelper;
    private final CryptoHelper cryptoHelper;
    private final SmsCodeService smsCodeService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (authentication.isAuthenticated()) {
            return authentication;
        }

        String phoneNumber = (String) authentication.getPrincipal();
        String encryptedSmsCode = (String) authentication.getCredentials();

        validateInput(phoneNumber, encryptedSmsCode);
        validateSmsCode(phoneNumber, encryptedSmsCode, authentication.getDetails());

        AuthUserCacheHelper.AuthUserSnapshot user = getUserByPhoneNumber(phoneNumber);
        if (user == null) {
            log.warn("用户不存在: phone={}", phoneNumber);
            throw new AuthenticationFailException(ResultCode.ACCOUNT_NOT_FOUND);
        }

        log.debug("手机号登录成功: userId={}", user.userId());
        return new MobileAuthenticationToken(user.userId(), null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return MobileAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private void validateInput(String phoneNumber, String smsCode) {
        if (!StringUtils.hasText(phoneNumber) || !StringUtils.hasText(smsCode)) {
            throw new AuthenticationFailException(ResultCode.VALIDATION_INCOMPLETE);
        }
    }

    private void validateSmsCode(String phoneNumber, String encryptedSmsCode, Object details) {
        String decryptedCode = cryptoHelper.decrypt(encryptedSmsCode);
        AuthRequestContext ctx = details instanceof AuthRequestContext c ? c : null;
        String clientIp = ctx != null ? ctx.getClientIp() : null;
        String deviceId = ctx != null ? ctx.getDeviceId() : null;

        SmsCodeService.VerifyResult result = smsCodeService.verifyAndConsume(phoneNumber, decryptedCode, deviceId, clientIp);
        if (result == SmsCodeService.VerifyResult.OK) {
            return;
        }
        if (result == SmsCodeService.VerifyResult.LOCKED) {
            throw new AuthenticationFailException(ResultCode.TOO_MANY_REQUESTS);
        }
        throw new AuthenticationFailException(ResultCode.CAPTCHA_ERROR);
    }

    private AuthUserCacheHelper.AuthUserSnapshot getUserByPhoneNumber(String phoneNumber) {
        return authUserCacheHelper.requireByMobile(phoneNumber);
    }
}
