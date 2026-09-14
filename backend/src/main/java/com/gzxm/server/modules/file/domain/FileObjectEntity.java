package com.gzxm.server.modules.file.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_object")
public class FileObjectEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String storageProvider;
    private String bucketName;
    private String objectKey;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private String status;
    private Long uploaderId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime deletedAt;
}
