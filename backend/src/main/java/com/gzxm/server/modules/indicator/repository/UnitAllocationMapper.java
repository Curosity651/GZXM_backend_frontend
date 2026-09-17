package com.gzxm.server.modules.indicator.repository;

import com.gzxm.server.modules.indicator.api.IndicatorDtos.AllocationView;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface UnitAllocationMapper {
    record Draft(long id,int draftVersion,int publishedDraftVersion,int publishVersion,int topicIndicatorVersion) {}
    record Publication(long topicId,long nodeId,int draftVersion) {}
    record Quantity(long unitId,long definitionId,long nodeId,int sortOrder,int quantity) {}

    @Select("SELECT id,draft_version,published_draft_version,publish_version,topic_indicator_version FROM unit_allocation_draft WHERE topic_id=#{topic} AND node_id=#{node}")
    Draft draft(@Param("topic") long topic,@Param("node") long node);
    @Insert("INSERT INTO unit_allocation_draft(topic_id,node_id,topic_indicator_version,updated_by) VALUES(#{topic},#{node},#{targetVersion},#{actor})")
    void createDraft(@Param("topic") long topic,@Param("node") long node,@Param("targetVersion") int targetVersion,@Param("actor") long actor);
    @Delete("DELETE FROM unit_allocation_draft_item WHERE draft_id=#{id}")
    void clearDraft(long id);
    @Insert("INSERT INTO unit_allocation_draft_item(draft_id,unit_id,indicator_definition_id,target_quantity) VALUES(#{draft},#{unit},#{definition},#{quantity})")
    void insertDraft(@Param("draft") long draft,@Param("unit") long unit,@Param("definition") long definition,@Param("quantity") int quantity);
    @Update("UPDATE unit_allocation_draft SET draft_version=draft_version+1,topic_indicator_version=#{targetVersion},updated_by=#{actor} WHERE id=#{id}")
    void bumpDraft(@Param("id") long id,@Param("targetVersion") int targetVersion,@Param("actor") long actor);
    @Select("SELECT CAST(a.id AS CHAR) id,CAST(d.topic_id AS CHAR) topic_id,CAST(a.unit_id AS CHAR) unit_id,CAST(d.node_id AS CHAR) node_id,CAST(a.indicator_definition_id AS CHAR) indicator_definition_id,a.target_quantity,'DRAFT' status,d.publish_version version FROM unit_allocation_draft_item a JOIN unit_allocation_draft d ON d.id=a.draft_id WHERE d.topic_id=#{topic} AND d.node_id=#{node} ORDER BY a.unit_id,a.indicator_definition_id")
    List<AllocationView> draftItems(@Param("topic") long topic,@Param("node") long node);
    @Select("<script>SELECT CAST(id AS CHAR) id,CAST(topic_id AS CHAR) topic_id,CAST(unit_id AS CHAR) unit_id,CAST(node_id AS CHAR) node_id,CAST(indicator_definition_id AS CHAR) indicator_definition_id,target_quantity,status,publish_version version FROM unit_indicator_allocation WHERE topic_id=#{topic} AND node_id=#{node} AND status='PUBLISHED' <if test='unit != null'>AND unit_id=#{unit}</if> ORDER BY unit_id,indicator_definition_id</script>")
    List<AllocationView> effective(@Param("topic") long topic,@Param("node") long node,@Param("unit") Long unit);
    @Select("SELECT a.unit_id,a.indicator_definition_id definition_id,a.node_id,n.sort_order,a.target_quantity quantity FROM unit_indicator_allocation a JOIN time_node n ON n.id=a.node_id WHERE a.topic_id=#{topic} AND a.status='PUBLISHED' ORDER BY n.sort_order")
    List<Quantity> effectiveQuantities(long topic);
    @Select("SELECT a.unit_id,a.indicator_definition_id definition_id,d.node_id,n.sort_order,a.target_quantity quantity FROM unit_allocation_draft_item a JOIN unit_allocation_draft d ON d.id=a.draft_id JOIN time_node n ON n.id=d.node_id WHERE d.topic_id=#{topic} AND d.draft_version>d.published_draft_version ORDER BY n.sort_order")
    List<Quantity> pendingQuantities(long topic);
    @Select("SELECT topic_id,node_id,draft_version FROM unit_allocation_publication WHERE published_by=#{actor} AND request_key=#{key}")
    Publication publication(@Param("actor") long actor,@Param("key") String key);
    @Insert("INSERT INTO unit_allocation_publication(topic_id,node_id,draft_version,publish_version,topic_indicator_version,published_by,request_key,allocations_json) VALUES(#{topic},#{node},#{revision},#{version},#{targetVersion},#{actor},#{key},#{json})")
    void recordPublication(@Param("topic") long topic,@Param("node") long node,@Param("revision") int revision,@Param("version") int version,
                           @Param("targetVersion") int targetVersion,@Param("actor") long actor,@Param("key") String key,@Param("json") String json);
    @Insert("INSERT INTO unit_indicator_allocation(project_id,topic_id,membership_id,unit_id,node_id,indicator_definition_id,topic_indicator_id,target_quantity,status,publish_version,published_by,published_at) VALUES(#{project},#{topic},#{membership},#{unit},#{node},#{definition},#{target},#{quantity},'PUBLISHED',#{version},#{actor},CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE membership_id=#{membership},topic_indicator_id=#{target},target_quantity=#{quantity},status='PUBLISHED',publish_version=#{version},published_by=#{actor},published_at=CURRENT_TIMESTAMP(3)")
    void publish(@Param("project") long project,@Param("topic") long topic,@Param("membership") long membership,@Param("unit") long unit,
                 @Param("node") long node,@Param("definition") long definition,@Param("target") long target,@Param("quantity") int quantity,
                 @Param("version") int version,@Param("actor") long actor);
    @Update("UPDATE unit_allocation_draft SET published_draft_version=draft_version,publish_version=#{version},updated_by=#{actor} WHERE id=#{id}")
    void markPublished(@Param("id") long id,@Param("version") int version,@Param("actor") long actor);
}
