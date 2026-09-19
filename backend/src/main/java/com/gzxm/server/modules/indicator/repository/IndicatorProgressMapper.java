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
    @Select("SELECT t.indicator_definition_id,SUM(t.target_quantity) target_quantity,MAX(t.publish_version) publish_version " +
            "FROM topic_indicator t JOIN time_node n ON n.id=t.node_id JOIN time_node selected ON selected.id=#{node} " +
            "WHERE t.topic_id=#{topic} AND t.status='PUBLISHED' AND n.project_id=selected.project_id AND n.sort_order<=selected.sort_order " +
            "GROUP BY t.indicator_definition_id ORDER BY t.indicator_definition_id")
    List<Target> targets(@Param("topic") long topic,@Param("node") long node);
    @Select("SELECT a.unit_id,a.indicator_definition_id,SUM(a.target_quantity) target_quantity,MAX(a.publish_version) publish_version " +
            "FROM unit_indicator_allocation a JOIN time_node n ON n.id=a.node_id JOIN time_node selected ON selected.id=#{node} " +
            "WHERE a.topic_id=#{topic} AND a.status='PUBLISHED' AND n.project_id=selected.project_id AND n.sort_order<=selected.sort_order " +
            "GROUP BY a.unit_id,a.indicator_definition_id ORDER BY a.unit_id,a.indicator_definition_id")
    List<Allocation> allocations(@Param("topic") long topic,@Param("node") long node);
}
