package com.cap.server.controller;

import com.cap.server.entity.App;
import com.cap.server.service.AppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final AppService appService;
    private final Path uploadDir = Paths.get("uploads/icons");

    public FileController(AppService appService) {
        this.appService = appService;
    }

    /** 上传 App 图标 */
    @PostMapping("/app-icon/{appId}")
    public ResponseEntity<?> uploadAppIcon(@PathVariable Long appId,
                                            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
            }

            // 校验文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of("error", "仅支持上传图片文件"));
            }

            // 校验文件大小（最大 1MB）
            if (file.getSize() > 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件大小不能超过 1MB"));
            }

            // 确保目录存在
            Files.createDirectories(uploadDir);

            // 生成唯一文件名
            String ext = "";
            String originalName = file.getOriginalFilename();
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String filename = "app-" + appId + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;

            // 保存文件
            Path targetPath = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // 更新 App 的 iconUrl
            String iconUrl = "/uploads/icons/" + filename;
            appService.updateIcon(appId, iconUrl);

            log.info("上传图标 appId={}: {} ({})", appId, filename, contentType);

            return ResponseEntity.ok(Map.of(
                    "message", "上传成功",
                    "iconUrl", iconUrl
            ));
        } catch (IOException e) {
            log.error("上传文件失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
