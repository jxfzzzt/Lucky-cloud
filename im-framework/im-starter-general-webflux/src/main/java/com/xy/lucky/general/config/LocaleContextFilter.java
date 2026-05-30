package com.xy.lucky.general.config;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.i18n.LocaleContextResolver;
import reactor.core.publisher.Mono;

/**
 * Locale 上下文同步过滤器
 *
 * <p>Spring WebFlux 的 {@link org.springframework.web.reactive.DispatcherHandler}
 * 只将 locale 存放在 {@link ServerWebExchange}，并不像 Spring MVC 的 DispatcherServlet
 * 那样自动同步到 {@link LocaleContextHolder}（ThreadLocal）。</p>
 *
 * <p>因此 Bean Validation 中 {@link org.springframework.validation.beanvalidation.LocalValidatorFactoryBean}
 * 的 {@code LocaleContextMessageInterpolator} 以及 {@link com.xy.lucky.general.response.service.I18nService}
 * 调用 {@link LocaleContextHolder#getLocale()} 时，都会拿到 JVM 默认 Locale 而非请求头
 * {@code Accept-Language} 中指定的语言。</p>
 *
 * <p>本过滤器在请求进入处理链之前，通过 {@link LocaleContextResolver} 将 {@code Accept-Language}
 * 解析结果写入 {@link LocaleContextHolder}（使用可继承的 ThreadLocal），从而使后续的
 * 参数校验、异常消息国际化等均能正确感知当前请求的语言环境；请求结束时自动清理，避免
 * 线程池复用时的 Locale 污染。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocaleContextFilter implements WebFilter {

    private final LocaleContextResolver localeContextResolver;

    public LocaleContextFilter(LocaleContextResolver localeContextResolver) {
        this.localeContextResolver = localeContextResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        LocaleContext localeContext = localeContextResolver.resolveLocaleContext(exchange);
        // inheritable=true：子线程（含虚拟线程池）可继承当前 Locale
        LocaleContextHolder.setLocaleContext(localeContext, true);
        return chain.filter(exchange)
                .doFinally(signal -> LocaleContextHolder.resetLocaleContext());
    }
}
