package com.gzxm.server.modules.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class UserEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String principalName;
    private String principalPhone;
    private String principalEmail;
    private String contactName;
    private String phone;
    private String email;
    private Long unitId;
    private String accountType;
    private Boolean enabled;
    private Integer tokenVersion;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    @TableField(exist = false)
    private String roleCode;
}
