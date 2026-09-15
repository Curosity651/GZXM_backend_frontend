package com.gzxm.server.modules.indicator.repository;

import com.gzxm.server.modules.indicator.api.IndicatorDtos.*;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface IndicatorCatalogMapper {
    @Select("SELECT CAST(id AS CHAR) id,name,deadline,sort_order,enabled FROM time_node WHERE project_id=#{projectId} AND enabled=1 ORDER BY sort_order,id")
    List<TimeNodeView> nodes(long projectId);

    @Select("SELECT CAST(id AS CHAR) id,code,name,achievement_type,unit_name AS unit,category,enabled FROM indicator_definition WHERE enabled=1 ORDER BY id")
    List<DefinitionView> definitions();
}
