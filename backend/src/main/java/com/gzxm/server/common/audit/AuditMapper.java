package com.gzxm.server.common.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface AuditMapper {
    @Insert("""
            INSERT INTO audit_log(user_id, username, action_code, resource_type, resource_id,
                                  request_id, ip_address, success_flag, detail_json)
            VALUES(#{userId}, #{username}, #{actionCode}, #{resourceType}, #{resourceId},
                   #{requestId}, #{ipAddress}, #{success}, CAST(#{detailJson} AS JSON))
            """)
    int insert(@Param("userId") Long userId, @Param("username") String username,
               @Param("actionCode") String actionCode, @Param("resourceType") String resourceType,
               @Param("resourceId") String resourceId, @Param("requestId") String requestId,
               @Param("ipAddress") String ipAddress, @Param("success") boolean success,
               @Param("detailJson") String detailJson);
}
