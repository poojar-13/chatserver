package com.chatapp.chatserver.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@CrossOrigin
public class FileController {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String ext = "";
            String original = file.getOriginalFilename();
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf("."));
            }

            String filename = UUID.randomUUID() + ext;
            Path path = Paths.get(uploadDir, filename);
            Files.write(path, file.getBytes());

            String fileUrl = "/api/files/" + filename;
            String fileType = file.getContentType();

            return ResponseEntity.ok(Map.of(
                "url", fileUrl,
                "type", fileType != null ? fileType : "application/octet-stream",
                "name", original != null ? original : filename
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Upload failed"));
        }
    }

    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> serveFile(
            @PathVariable String filename
    ) {
        try {
            Path path = Paths.get(uploadDir, filename);
            byte[] bytes = Files.readAllBytes(path);

            String contentType = Files.probeContentType(path);
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .body(bytes);

        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}