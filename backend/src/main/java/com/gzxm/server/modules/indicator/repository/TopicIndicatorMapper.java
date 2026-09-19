package com.gzxm.server.modules.indicator.repository;

import com.gzxm.server.modules.indicator.api.IndicatorDtos.TargetView;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface TopicIndicatorMapper {
    record Node(long id,long projectId,String name,int sortOrder,boolean enabled) {}
    record Definition(long id,String name,String achievementType,String category,boolean enabled) {}
    record Draft(long id,int draftVersion,int publishedDraftVersion,int publishVersion) {}
    record Publication(long topicId,long nodeId,int draftVersion) {}
    record Quantity(long definitionId,String definitionName,long nodeId,String nodeName,int sortOrder,int quantity) {}

    @Select("SELECT id,project_id,name,sort_order,enabled FROM time_node WHERE id=#{id}")
    Node node(long id);
    @Select("SELECT id,name,achievement_type,category,enabled FROM indicator_definition ORDER BY id")
    List<Definition> definitions();
    @Select("SELECT id,draft_version,published_draft_version,publish_version FROM topic_indicator_draft WHERE topic_id=#{topic} AND node_id=#{node}")
    Draft draft(@Param("topic") long topic,@Param("node") long node);
    @Insert("INSERT INTO topic_indicator_draft(topic_id,node_id,updated_by) VALUES(#{topic},#{node},#{actor})")
    void createDraft(@Param("topic") long topic,@Param("node") long node,@Param("actor") long actor);
    @Delete("DELETE FROM topic_indicator_draft_target WHERE draft_id=#{draft}")
    void clearDraft(long draft);
    @Insert("INSERT INTO topic_indicator_draft_target(draft_id,indicator_definition_id,target_quantity) VALUES(#{draft},#{definition},#{quantity})")
    void insertDraftTarget(@Param("draft") long draft,@Param("definition") long definition,@Param("quantity") int quantity);
    @Update("UPDATE topic_indicator_draft SET draft_version=draft_version+1,updated_by=#{actor} WHERE id=#{id}")
    void bumpDraft(@Param("id") long id,@Param("actor") long actor);
    @Select("SELECT CAST(t.id AS CHAR) id,CAST(d.topic_id AS CHAR) topic_id,CAST(d.node_id AS CHAR) node_id,CAST(t.indicator_definition_id AS CHAR) indicator_definition_id,t.target_quantity,'DRAFT' status,d.publish_version version FROM topic_indicator_draft_target t JOIN topic_indicator_draft d ON d.id=t.draft_id WHERE d.topic_id=#{topic} AND d.node_id=#{node} ORDER BY t.indicator_definition_id")
    List<TargetView> draftTargets(@Param("topic") long topic,@Param("node") long node);
    @Select("SELECT CAST(id AS CHAR) id,CAST(topic_id AS CHAR) topic_id,CAST(node_id AS CHAR) node_id,CAST(indicator_definition_id AS CHAR) indicator_definition_id,target_quantity,status,publish_version version FROM topic_indicator WHERE topic_id=#{topic} AND node_id=#{node} AND status='PUBLISHED' ORDER BY indicator_definition_id")
    List<TargetView> effective(@Param("topic") long topic,@Param("node") long node);
    @Select("SELECT t.indicator_definition_id definition_id,d.name definition_name,t.node_id,n.name node_name,n.sort_order,t.target_quantity quantity FROM topic_indicator t JOIN time_node n ON n.id=t.node_id JOIN indicator_definition d ON d.id=t.indicator_definition_id WHERE t.topic_id=#{topic} AND t.status='PUBLISHED' ORDER BY n.sort_order")
    List<Quantity> effectiveQuantities(long topic);
    @Select("SELECT t.indicator_definition_id definition_id,i.name definition_name,d.node_id,n.name node_name,n.sort_order,t.target_quantity quantity FROM topic_indicator_draft_target t JOIN topic_indicator_draft d ON d.id=t.draft_id JOIN time_node n ON n.id=d.node_id JOIN indicator_definition i ON i.id=t.indicator_definition_id WHERE d.topic_id=#{topic} AND d.draft_version>d.published_draft_version ORDER BY n.sort_order")
    List<Quantity> pendingQuantities(long topic);
    @Select("SELECT topic_id,node_id,draft_version FROM topic_indicator_publication WHERE published_by=#{actor} AND request_key=#{key}")
    Publication publication(@Param("actor") long actor,@Param("key") String key);
    @Insert("INSERT INTO topic_indicator_publication(topic_id,node_id,draft_version,publish_version,published_by,request_key,targets_json) VALUES(#{topic},#{node},#{draftVersion},#{version},#{actor},#{key},#{json})")
    void recordPublication(@Param("topic") long topic,@Param("node") long node,@Param("draftVersion") int draftVersion,
                           @Param("version") int version,@Param("actor") long actor,@Param("key") String key,@Param("json") String json);
    @Insert("INSERT INTO topic_indicator(project_id,topic_id,node_id,indicator_definition_id,target_quantity,status,publish_version,published_by,published_at) VALUES(#{project},#{topic},#{node},#{definition},#{quantity},'PUBLISHED',#{version},#{actor},CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE target_quantity=#{quantity},status='PUBLISHED',publish_version=#{version},published_by=#{actor},published_at=CURRENT_TIMESTAMP(3)")
    void publishTarget(@Param("project") long project,@Param("topic") long topic,@Param("node") long node,
                       @Param("definition") long definition,@Param("quantity") int quantity,@Param("version") int version,@Param("actor") long actor);
    @Update("UPDATE topic_indicator_draft SET published_draft_version=draft_version,publish_version=#{version},updated_by=#{actor} WHERE id=#{id}")
    void markPublished(@Param("id") long id,@Param("version") int version,@Param("actor") long actor);
}
