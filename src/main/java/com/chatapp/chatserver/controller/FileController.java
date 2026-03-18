package com.chatapp.chatserver.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@CrossOrigin
public class FileController {

    private final Cloudinary cloudinary;

    public FileController(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            // Upload to Cloudinary
            Map uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "folder", "chatapp",
                    "resource_type", "auto"  // handles images, videos, files
                )
            );

            String fileUrl = (String) uploadResult.get("secure_url");
            String fileType = file.getContentType();
            String fileName = file.getOriginalFilename();

            return ResponseEntity.ok(Map.of(
                "url", fileUrl,
                "type", fileType != null ? fileType : "application/octet-stream",
                "name", fileName != null ? fileName : "file"
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }
}