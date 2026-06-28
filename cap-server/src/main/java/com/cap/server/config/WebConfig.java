package com.cap.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 暴露 uploads/ 目录为静态资源
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}
