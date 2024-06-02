package com.trionesdev.spring.core.context;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class SpringContextSetter implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        SpringContextHolder.setApplicationContext(configurableApplicationContext);
    }
}
