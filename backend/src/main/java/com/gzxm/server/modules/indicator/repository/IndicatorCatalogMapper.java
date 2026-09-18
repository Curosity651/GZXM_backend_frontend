package com.gzxm.server.modules.indicator.repository;

import com.gzxm.server.modules.indicator.api.IndicatorDtos.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface IndicatorCatalogMapper {
    @Select("SELECT CAST(id AS CHAR) id,code,name,deadline,sort_order,enabled FROM time_node WHERE project_id=#{projectId} AND (enabled=1 OR #{includeDisabled}=1) ORDER BY sort_order,id")
    List<TimeNodeView> nodes(@Param("projectId") long projectId, @Param("includeDisabled") boolean includeDisabled);

    @Select("SELECT CAST(id AS CHAR) id,code,name,deadline,sort_order,enabled FROM time_node WHERE id=#{id}")
    TimeNodeView node(long id);

    @Insert("INSERT INTO time_node(project_id,code,name,deadline,sort_order,enabled) VALUES(#{projectId},#{code},#{name},#{deadline},#{sortOrder},1)")
    int insertNode(@Param("projectId") long projectId, @Param("code") String code, @Param("name") String name,
                   @Param("deadline") java.time.LocalDate deadline, @Param("sortOrder") int sortOrder);

    @Select("SELECT LAST_INSERT_ID()")
    long lastId();

    @Update("UPDATE time_node SET name=#{name},deadline=#{deadline},sort_order=#{sortOrder} WHERE id=#{id}")
    int updateNode(@Param("id") long id, @Param("name") String name,
                   @Param("deadline") java.time.LocalDate deadline, @Param("sortOrder") int sortOrder);

    @Update("UPDATE time_node SET enabled=#{enabled} WHERE id=#{id}")
    int setNodeStatus(@Param("id") long id, @Param("enabled") boolean enabled);

    @Select("SELECT COUNT(*) FROM time_node WHERE project_id=#{projectId} AND sort_order=#{sortOrder} AND id<>#{excludeId}")
    int countSortOrder(@Param("projectId") long projectId, @Param("sortOrder") int sortOrder, @Param("excludeId") long excludeId);

    @Select("SELECT (SELECT COUNT(*) FROM topic_indicator WHERE node_id=#{id})+(SELECT COUNT(*) FROM topic_indicator_draft WHERE node_id=#{id})+(SELECT COUNT(*) FROM unit_indicator_allocation WHERE node_id=#{id})+(SELECT COUNT(*) FROM unit_allocation_draft WHERE node_id=#{id})")
    int nodeUsage(long id);

    @Select("SELECT CAST(id AS CHAR) id,code,name,achievement_type,unit_name AS unit,category,enabled FROM indicator_definition WHERE enabled=1 ORDER BY id")
    List<DefinitionView> definitions();
}
