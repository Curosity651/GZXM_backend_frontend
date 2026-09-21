package com.gzxm.server.modules.systemlog.repository;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

public interface SystemErrorLogMapper {
    record Row(long id, String traceId, String severity, Long userId, String username,
               String httpMethod, String requestPath, int statusCode, String errorCode,
               String errorMessage, String exceptionClass, String stackSummary,
               long durationMs, String clientIp, String userAgent, LocalDateTime createdAt) {}

    @Insert("""
            INSERT INTO system_error_log(trace_id,severity,user_id,username,http_method,request_path,status_code,
              error_code,error_message,exception_class,stack_summary,duration_ms,client_ip,user_agent)
            VALUES(#{traceId},#{severity},#{userId},#{username},#{httpMethod},#{requestPath},#{statusCode},
              #{errorCode},#{errorMessage},#{exceptionClass},#{stackSummary},#{durationMs},#{clientIp},#{userAgent})
            """)
    int insert(@Param("traceId") String traceId, @Param("severity") String severity,
               @Param("userId") Long userId, @Param("username") String username,
               @Param("httpMethod") String httpMethod, @Param("requestPath") String requestPath,
               @Param("statusCode") int statusCode, @Param("errorCode") String errorCode,
               @Param("errorMessage") String errorMessage, @Param("exceptionClass") String exceptionClass,
               @Param("stackSummary") String stackSummary, @Param("durationMs") long durationMs,
               @Param("clientIp") String clientIp, @Param("userAgent") String userAgent);

    @Select("""
            <script>
            SELECT id,trace_id,severity,user_id,username,http_method,request_path,status_code,error_code,
                   error_message,exception_class,stack_summary,duration_ms,client_ip,user_agent,created_at
            FROM system_error_log
            <where>
              <if test='severity != null and severity != ""'>AND severity=#{severity}</if>
              <if test='statusCode != null'>AND status_code=#{statusCode}</if>
              <if test='username != null and username != ""'>AND username LIKE CONCAT('%',#{username},'%')</if>
              <if test='traceId != null and traceId != ""'>AND trace_id=#{traceId}</if>
              <if test='from != null'>AND created_at &gt;= #{from}</if>
              <if test='to != null'>AND created_at &lt;= #{to}</if>
            </where>
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Row> list(@Param("severity") String severity, @Param("statusCode") Integer statusCode,
                   @Param("username") String username, @Param("traceId") String traceId,
                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                   @Param("offset") long offset, @Param("limit") long limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM system_error_log
            <where>
              <if test='severity != null and severity != ""'>AND severity=#{severity}</if>
              <if test='statusCode != null'>AND status_code=#{statusCode}</if>
              <if test='username != null and username != ""'>AND username LIKE CONCAT('%',#{username},'%')</if>
              <if test='traceId != null and traceId != ""'>AND trace_id=#{traceId}</if>
              <if test='from != null'>AND created_at &gt;= #{from}</if>
              <if test='to != null'>AND created_at &lt;= #{to}</if>
            </where>
            </script>
            """)
    long count(@Param("severity") String severity, @Param("statusCode") Integer statusCode,
               @Param("username") String username, @Param("traceId") String traceId,
               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
