package com.cap.server.controller;

import com.cap.server.entity.User;
import com.cap.server.service.UserService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 系统启动时，如果没有管理员账号，自动创建一个默认管理员。
 */
@Component
public class AdminInitController {

    private static final Logger log = LoggerFactory.getLogger(AdminInitController.class);

    private final UserService userService;

    public AdminInitController(UserService userService) {
        this.userService = userService;
    }

    @PostConstruct
    public void initAdmin() {
        try {
            if (userService.findByUsername("admin").isEmpty()) {
                userService.register("admin", "admin123", "admin@localhost", "Administrator");
                log.info("已创建默认管理员账号: admin / admin123");
            }
        } catch (Exception e) {
            log.warn("初始化管理员账号失败（可能非首次启动）: {}", e.getMessage());
        }
    }
}
