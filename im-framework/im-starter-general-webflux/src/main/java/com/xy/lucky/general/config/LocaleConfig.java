package com.xy.lucky.general.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver;
import org.springframework.web.server.i18n.LocaleContextResolver;

import java.time.ZoneOffset;
import java.util.Locale;
import java.util.TimeZone;


/**
 * 国际化配置类（Locale & MessageSource）
 * <p>
 * 用于支持基于请求头（Accept-Language）或自定义逻辑的语言切换。
 * 默认加载路径：classpath:i18n/messages_*.properties
 * 业务模块可通过 {@code lucky.i18n.basenames} 追加额外的 ResourceBundle basename，
 * 例如 {@code i18n/messages-business}。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(I18nProperties.class)
public class LocaleConfig {

    /**
     * 设置默认时区为UTC
     */
    @PostConstruct
    void setTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
        log.info("Set default timezone to: {}", ZoneOffset.UTC);
    }

    /**
     * Locale 解析器：根据请求头 Accept-Language 自动识别语言
     * 默认语言为英文（en）
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "localeResolver")
    public LocaleContextResolver localeResolver() {
        AcceptHeaderLocaleContextResolver resolver = new AcceptHeaderLocaleContextResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setSupportedLocales(java.util.List.of(
                Locale.ENGLISH,
                Locale.SIMPLIFIED_CHINESE,
                Locale.US,
                Locale.CHINA
        ));
        return resolver;
    }

    /**
     * 消息源配置：支持加载多个 basename，便于各业务模块独立维护 i18n 资源包。
     */
    @Bean
    @ConditionalOnMissingBean(MessageSource.class)
    public MessageSource messageSource(I18nProperties properties) {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        String[] basenames = properties.getBasenames().toArray(new String[0]);
        source.setBasenames(basenames);
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        source.setCacheSeconds(0);
        return source;
    }

    /**
     * 注入基于 MessageSource 的 Validator，使 DTO 上 {@code @NotBlank(message = "{validation.xxx.required}")}
     * 这类 {key} 占位符可以通过 Spring 的 MessageSource 完成 i18n 解析。
     *
     * <p>仅当类路径下存在 jakarta.validation 与 hibernate-validator 实现时启用。</p>
     */
    @Bean
    @ConditionalOnClass(name = {
            "jakarta.validation.Validator",
            "org.hibernate.validator.HibernateValidator"
    })
    @ConditionalOnMissingBean(LocalValidatorFactoryBean.class)
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setValidationMessageSource(messageSource);
        return factoryBean;
    }

    /**
     * Locale 上下文同步过滤器：将每次请求的 {@code Accept-Language} 解析结果写入
     * {@link org.springframework.context.i18n.LocaleContextHolder}。
     *
     * <p>Spring WebFlux 的 DispatcherHandler 不会像 Spring MVC 的 DispatcherServlet
     * 那样自动同步 Locale 到 ThreadLocal，导致 Bean Validation 的消息插值和
     * {@link com.xy.lucky.general.response.service.I18nService} 均无法感知请求语言。
     * 本过滤器补齐了这一差异，确保 i18n 在 WebFlux 下按请求头正常工作。</p>
     */
    @Bean
    @ConditionalOnMissingBean(LocaleContextFilter.class)
    public LocaleContextFilter localeContextFilter(
            @Qualifier("localeResolver") LocaleContextResolver localeResolver) {
        return new LocaleContextFilter(localeResolver);
    }
}
