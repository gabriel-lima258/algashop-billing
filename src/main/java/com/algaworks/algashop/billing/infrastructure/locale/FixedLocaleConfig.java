package com.algaworks.algashop.billing.infrastructure.locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Locale;

// classe de configuração de locale - define o tipo de lingua a ser exibida
// - English foi escolhido por se tratar de uma api internacional

@Configuration
public class FixedLocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.ENGLISH);
    }
}
