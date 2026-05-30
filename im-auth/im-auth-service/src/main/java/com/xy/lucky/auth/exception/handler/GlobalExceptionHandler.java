package com.xy.lucky.auth.exception.handler;


import com.xy.lucky.auth.exception.ResponseNotIntercept;
import com.xy.lucky.general.response.domain.Result;
import com.xy.lucky.general.response.domain.ResultCode;
import com.xy.lucky.utils.json.JacksonUtils;
import com.xy.lucky.security.exception.AuthenticationFailException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.naming.SizeLimitExceededException;
import java.rmi.ServerException;

/**
 * 全局异常处理
 *
 * @author dense
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.xy.lucky.auth")
@Order(Ordered.HIGHEST_PRECEDENCE)// 设置最高优先级
public class GlobalExceptionHandler implements ResponseBodyAdvice<Object> {

    /**
     * 自定义认证异常（业务可预期）
     */
    @ExceptionHandler(AuthenticationFailException.class)
    public Result<?> handleAuthenticationFailException(AuthenticationFailException ex) {
        log.warn("认证失败: code={}, message={}", ex.getResultCode().getCode(), ex.getResultCode().getMessage());
        return Result.failed(ex.getResultCode());
    }

    /**
     * 自定义认证异常
     *
     * @param ex
     * @return
     */
    @ExceptionHandler(AuthenticationException.class)
    public Result<?> handleAuthenticationException(AuthenticationException ex) {
        log.warn("认证异常: {}", ex.getMessage());
        return Result.failed(ResultCode.AUTHENTICATION_FAILED);
    }

    /**
     * 处理Validated验证异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler({BindException.class})
    public Result<?> bindExceptionHandler(BindException e) {
        ObjectError objectError = e.getBindingResult().getAllErrors().get(0);
        log.warn("参数校验失败: {}", objectError.getDefaultMessage());
        return Result.failed(ResultCode.BAD_REQUEST.getCode(), objectError.getDefaultMessage());
    }


    /**
     * 处理请求数据超大异常
     *
     * @param e
     * @return
     * @ExceptionHandler
     */
    @ExceptionHandler(SizeLimitExceededException.class)
    public Result<?> sizeLimitExceededExceptionHandler(SizeLimitExceededException e) {
        log.warn("请求数据过大: {}", e.getMessage());
        // "请求数据大小不允许超过10MB"
        return Result.failed(ResultCode.REQUEST_DATA_TOO_LARGE);
    }


    /**
     * 空值异常
     *
     * @param ex
     * @return
     */
    @ExceptionHandler(value = NullPointerException.class)
    public Result<?> handleNullPointerException(NullPointerException ex) {
        // 对空指针异常的处理逻辑
        log.error("空指针异常", ex);
        return Result.failed(ResultCode.INTERNAL_SERVER_ERROR);
    }


    /**
     * 服务异常
     *
     * @param ex
     * @return
     */
    @ExceptionHandler(ServerException.class)
    public Result<?> handleServerException(ServerException ex) {
        log.error("Server error: {}", ex.getMessage(), ex);
        // 服务异常
        return Result.failed(ResultCode.SERVICE_EXCEPTION);
    }

    /**
     * 权限不足
     *
     * @param ex
     * @return
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage(), ex);
        // 没有权限
        return Result.failed(ResultCode.NO_PERMISSION);
    }

    /**
     * 通用异常处理
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleGeneralException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        // 服务器内部异常，请稍后重试
        return Result.failed(ResultCode.INTERNAL_SERVER_ERROR);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (returnType.getDeclaringClass().isAnnotationPresent(ResponseNotIntercept.class)) {
            return false;
        }
        if (returnType.getMethod() != null && returnType.getMethod().isAnnotationPresent(ResponseNotIntercept.class)) {
            return false;
        }
        return true;
    }

    /**
     * 统一响应封装，确保业务接口返回 Result JSON 结构。
     */
    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result) {
            return body;
        }
        Result<?> wrapped = Result.success(body);
        if (body instanceof String) {
            return JacksonUtils.toJSONString(wrapped);
        }
        return wrapped;
    }

}
