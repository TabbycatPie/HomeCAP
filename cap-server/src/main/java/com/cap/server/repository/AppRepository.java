package com.cap.server.repository;

import com.cap.server.entity.App;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppRepository extends JpaRepository<App, Long> {
    Optional<App> findByAppType(String appType);
}
