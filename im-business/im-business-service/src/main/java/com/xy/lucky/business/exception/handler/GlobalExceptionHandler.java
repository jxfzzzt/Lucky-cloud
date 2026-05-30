package com.xy.lucky.business.exception.handler;

import com.xy.lucky.general.exception.BusinessException;
import com.xy.lucky.general.exception.ForbiddenException;
import com.xy.lucky.general.exception.GlobalException;
import com.xy.lucky.general.response.domain.Result;
import com.xy.lucky.general.response.domain.ResultCode;
import com.xy.lucky.general.response.service.I18nService;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.nio.file.AccessDeniedException;
import java.rmi.ServerException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 全局异常处理
 *
 * <p>所有面向客户端的错误描述均通过 {@link I18nService} 解析当前 {@link java.util.Locale}，
 * 配合 {@code im-business} 模块的 {@code i18n/messages-business_*.properties} 提供多语言支持。</p>
 *
 * @author dense
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.xy.lucky")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    /**
     * 形如 {validation.xxx} 的 i18n 占位符，用于在校验消息未被
     * {@link org.springframework.validation.beanvalidation.LocalValidatorFactoryBean}
     * 提前解析时进行兜底替换。
     */
    private static final Pattern I18N_PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");

    /**
     * 处理自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handle(BusinessException ex) {
        log.error("BusinessException [{}]: {}", ex.getCode(), ex.getMessage(), ex);
        return Result.failed(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(GlobalException.class)
    public Result<?> handle(GlobalException ex) {
        log.error("GlobalException [{}]: {}", ex.getCode(), ex.getMessage(), ex);
        return Result.failed(ex.getCode(), ex.getMessage());
    }

    /**
     * 处理 RequestBody 校验失败
     *
     * <p>聚合所有字段错误为 {@code field: message, ...}；若 message 仍包含未解析的
     * i18n 占位符（如 {@code {validation.xxx}}），通过 {@link I18nService}
     * 兜底替换为当前 Locale 文案。</p>
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Result<?> handle(WebExchangeBindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + resolvePlaceholders(error.getDefaultMessage()))
                .collect(Collectors.joining(", "));
        log.warn("WebExchangeBindException: {}", msg);
        return Result.failed(ResultCode.VALIDATION_INCOMPLETE, msg);
    }

    /**
     * 处理 PathVariable / RequestParam 校验失败
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handle(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> resolvePlaceholders(v.getMessage()))
                .collect(Collectors.joining(", "));
        log.warn("ConstraintViolationException: {}", msg);
        return Result.failed(ResultCode.VALIDATION_INCOMPLETE, msg);
    }

    /**
     * 处理禁止访问异常
     */
    @ExceptionHandler(ForbiddenException.class)
    public Result<?> handle(ForbiddenException ex) {
        log.error("ForbiddenException: {}", ex.getMessage(), ex);
        return Result.failed(ResultCode.FORBIDDEN);
    }

    /**
     * 处理访问权限异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handle(AccessDeniedException ex) {
        log.warn("AccessDeniedException: {}", ex.getMessage(), ex);
        return Result.failed(ResultCode.NO_PERMISSION);
    }

    /**
     * 处理大文件上传异常
     */
    @ExceptionHandler(DataBufferLimitException.class)
    public Result<?> handle(DataBufferLimitException ex) {
        log.error("DataBufferLimitException: {}", ex.getMessage(), ex);
        return Result.failed(ResultCode.REQUEST_DATA_TOO_LARGE);
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public Result<?> handle(NullPointerException ex) {
        log.error("NullPointerException: {}", ex.getMessage(), ex);
        return Result.failed(ResultCode.NOT_FOUND);
    }

    /**
     * 处理服务异常
     */
    @ExceptionHandler(ServerException.class)
    public Result<?> handle(ServerException ex) {
        log.error("ServerException: {}", ex.getMessage(), ex);
        return Result.failed(ResultCode.SERVICE_EXCEPTION);
    }

    /**
     * 统一异常处理
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handle(Exception ex) {
        log.error("Unhandled Exception: {}", ex.getMessage(), ex);
        return Result.failed(ResultCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 把字符串中残留的 {@code {key}} 占位符通过 {@link I18nService} 替换为本地化文案。
     * 兜底方案，正常情况下 LocalValidatorFactoryBean 已完成解析。
     */
    private static String resolvePlaceholders(String raw) {
        if (raw == null || raw.isEmpty() || raw.indexOf('{') < 0) {
            return raw;
        }
        Matcher matcher = I18N_PLACEHOLDER.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String localized = I18nService.getMessage(key);
            // I18nService 在缺失 key 时会返回 key 本身，避免回写成空串
            matcher.appendReplacement(sb, Matcher.quoteReplacement(localized));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
