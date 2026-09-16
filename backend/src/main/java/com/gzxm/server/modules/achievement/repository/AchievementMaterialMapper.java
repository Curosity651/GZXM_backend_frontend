package com.gzxm.server.modules.achievement.repository;
import org.apache.ibatis.annotations.*;
import java.util.List;
public interface AchievementMaterialMapper {
    record Material(long id,long fileId,String materialType,int fileVersion,boolean active,String materialStatus) {}
    @Select("SELECT id,file_id,material_type,file_version,active,material_status FROM achievement_material WHERE achievement_id=#{id} ORDER BY file_version DESC,id")
    List<Material> list(long id);
    @Select("SELECT DISTINCT achievement_id FROM achievement_material WHERE file_id=#{fileId}")
    List<Long> achievements(long fileId);
    @Update("UPDATE achievement_material SET active=0 WHERE achievement_id=#{id} AND active=1")
    void retire(long id);
    @Insert("INSERT INTO achievement_material(achievement_id,file_id,material_type,file_version,material_status,active,created_by) VALUES(#{achievement},#{file},#{type},#{version},'UNSUBMITTED',1,#{actor})")
    void insert(@Param("achievement") long achievement,@Param("file") long file,@Param("type") String type,@Param("version") int version,@Param("actor") long actor);
    @Update("UPDATE achievement_material SET material_status=#{status} WHERE achievement_id=#{id} AND active=1")
    void markCurrent(@Param("id") long id,@Param("status") String status);
}
