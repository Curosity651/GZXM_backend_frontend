package com.gzxm.server.modules.indicator.repository;

import org.apache.ibatis.annotations.*;
import java.util.List;
import com.gzxm.server.modules.indicator.application.IndicatorProgressQuery.*;

public interface IndicatorProgressMapper {
    record DefinitionRow(long id,String code,String name,String achievementType,String category,boolean enabled,String matchRule) {}
    @Select("SELECT id,project_id,sort_order,enabled FROM time_node WHERE id=#{id}")
    Node node(long id);
    @Select("SELECT id FROM time_node WHERE project_id=#{project} AND sort_order <= #{sort} ORDER BY sort_order,id")
    List<Long> nodes(@Param("project") long project,@Param("sort") int sort);
    @Select("SELECT id,code,name,achievement_type,category,enabled,match_rule FROM indicator_definition ORDER BY id")
    List<DefinitionRow> definitions();
    @Select("SELECT indicator_definition_id,target_quantity,publish_version FROM topic_indicator WHERE topic_id=#{topic} AND node_id=#{node} AND status='PUBLISHED' ORDER BY indicator_definition_id")
    List<Target> targets(@Param("topic") long topic,@Param("node") long node);
    @Select("SELECT unit_id,indicator_definition_id,target_quantity,publish_version FROM unit_indicator_allocation WHERE topic_id=#{topic} AND node_id=#{node} AND status='PUBLISHED' ORDER BY unit_id,indicator_definition_id")
    List<Allocation> allocations(@Param("topic") long topic,@Param("node") long node);
}
