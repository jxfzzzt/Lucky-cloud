package com.xy.lucky.general.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 国际化资源配置属性
 *
 * <p>支持配置多个 ResourceBundle basename，便于业务模块在自身 resources/i18n
 * 目录下独立维护一套 messages-xxx_*.properties，并通过 {@code lucky.i18n.basenames}
 * 列表注册到全局 {@link org.springframework.context.MessageSource}。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lucky.i18n")
public class I18nProperties {

    /**
     * MessageSource basename 列表。
     *
     * <p>默认仅包含框架自带的 {@code i18n/messages}，业务模块可通过配置追加，例如：
     * <pre>
     * lucky:
     *   i18n:
     *     basenames:
     *       - i18n/messages
     *       - i18n/messages-business
     * </pre>
     */
    private List<String> basenames = new ArrayList<>(List.of("i18n/messages"));
}
