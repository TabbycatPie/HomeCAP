package com.cap.server.repository;

import com.cap.server.entity.UserAppPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserAppPermissionRepository extends JpaRepository<UserAppPermission, Long> {
    List<UserAppPermission> findByUserId(Long userId);
    List<UserAppPermission> findByAppId(Long appId);
    Optional<UserAppPermission> findByUserIdAndAppId(Long userId, Long appId);
    boolean existsByUserIdAndAppIdAndStatus(Long userId, Long appId, String status);

    void deleteByUserId(Long userId);
    void deleteByAppId(Long appId);
}
