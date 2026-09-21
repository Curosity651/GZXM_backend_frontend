package com.gzxm.server.modules.file.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzxm.server.modules.file.domain.FileObjectEntity;
import org.apache.ibatis.annotations.Select;

public interface FileObjectMapper extends BaseMapper<FileObjectEntity> {
    @Select("SELECT * FROM file_object WHERE id=#{id} FOR UPDATE")
    FileObjectEntity lockById(long id);
}
