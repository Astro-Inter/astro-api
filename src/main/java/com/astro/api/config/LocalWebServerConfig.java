package com.astro.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "astro.local.nio2", havingValue = "true")
public class LocalWebServerConfig {
    @Bean
    TomcatServletWebServerFactory localTomcatFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
        return factory;
    }
}
