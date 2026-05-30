package com.xy.lucky.auth.security.provider;

import com.xy.lucky.auth.security.helper.AuthUserCacheHelper;
import com.xy.lucky.auth.security.helper.CryptoHelper;
import com.xy.lucky.general.response.domain.ResultCode;
import com.xy.lucky.security.exception.AuthenticationFailException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 用户名密码认证提供者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsernamePasswordAuthenticationProvider implements AuthenticationProvider {

    private final AuthUserCacheHelper authUserCacheHelper;
    private final CryptoHelper cryptoHelper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String userId = (String) authentication.getPrincipal();
        String encryptedPassword = (String) authentication.getCredentials();

        AuthUserCacheHelper.AuthUserSnapshot user = getUserByUserId(userId);
        String decryptedPassword = cryptoHelper.decrypt(encryptedPassword);

        if (!passwordEncoder.matches(decryptedPassword, user.password())) {
            log.warn("密码错误: userId={}", userId);
            throw new AuthenticationFailException(ResultCode.INVALID_CREDENTIALS);
        }

        log.debug("用户登录成功: userId={}", userId);
        return createAuthenticationToken(user, authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private AuthUserCacheHelper.AuthUserSnapshot getUserByUserId(String userId) {
        AuthUserCacheHelper.AuthUserSnapshot user = authUserCacheHelper.requireByUserId(userId);
        if (user == null) {
            log.warn("用户不存在: userId={}", userId);
            throw new AuthenticationFailException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        return user;
    }

    private Authentication createAuthenticationToken(AuthUserCacheHelper.AuthUserSnapshot user, Authentication authentication) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                user.userId(), user.password(), null);
        token.setDetails(authentication.getDetails());
        return token;
    }
}
