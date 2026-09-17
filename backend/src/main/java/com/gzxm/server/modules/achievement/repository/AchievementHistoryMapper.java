package com.gzxm.server.modules.achievement.repository;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** Shared tables, strictly limited to B's ACHIEVEMENT rows; no report data access. */
public interface AchievementHistoryMapper {
    record Snapshot(long id,long businessId,String stage,int submittedVersion,long submitterId,String payloadJson,LocalDateTime submittedAt) {}
    record Approval(long id,long businessId,String stage,String approvalLevel,String decision,String opinion,long operatorId,int submittedVersion,LocalDateTime operatedAt) {}

    @Select("SELECT id,business_id,stage,submitted_version,submitter_id,payload_json,submitted_at FROM submission_snapshot WHERE business_type='ACHIEVEMENT' AND business_id=#{id} ORDER BY submitted_version DESC")
    List<Snapshot> snapshots(long id);

    @Select("SELECT id,business_id,stage,approval_level,decision,opinion,operator_id,submitted_version,operated_at FROM approval_record WHERE business_type='ACHIEVEMENT' AND business_id=#{id} ORDER BY id")
    List<Approval> approvals(long id);

    @Insert("INSERT INTO submission_snapshot(business_type,business_id,stage,submitted_version,submitter_id,payload_json) VALUES('ACHIEVEMENT',#{id},#{stage},#{version},#{actor},#{payload})")
    void snapshot(@Param("id") long id,@Param("stage") String stage,@Param("version") int version,@Param("actor") long actor,@Param("payload") String payload);

    @Insert("INSERT INTO approval_record(business_type,business_id,stage,approval_level,decision,opinion,operator_id,submitted_version) VALUES('ACHIEVEMENT',#{id},#{stage},#{level},#{decision},#{opinion},#{actor},#{version})")
    void approve(@Param("id") long id,@Param("stage") String stage,@Param("level") String level,@Param("decision") String decision,
                 @Param("opinion") String opinion,@Param("actor") long actor,@Param("version") int version);
}
