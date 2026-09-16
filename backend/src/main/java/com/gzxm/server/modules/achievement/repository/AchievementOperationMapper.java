package com.gzxm.server.modules.achievement.repository;
import org.apache.ibatis.annotations.*;

public interface AchievementOperationMapper {
    record Operation(long achievementId,String operationKind,String requestJson,String responseJson) {}
    @Select("SELECT achievement_id,operation_kind,request_json,response_json FROM achievement_workflow_operation WHERE actor_id=#{actor} AND request_key=#{key} FOR UPDATE")
    Operation find(@Param("actor") long actor,@Param("key") String key);
    @Insert("INSERT INTO achievement_workflow_operation(achievement_id,actor_id,request_key,operation_kind,request_json,response_json) VALUES(#{id},#{actor},#{key},#{kind},#{request},#{response})")
    void insert(@Param("id") long id,@Param("actor") long actor,@Param("key") String key,@Param("kind") String kind,@Param("request") String request,@Param("response") String response);
}
