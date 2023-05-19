package com.chua.starter.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.server.pojo.Doorplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author CH
 */
@Mapper
public interface DoorplateMapper extends BaseMapper<Doorplate> {

    List<Doorplate> test(String id);

    List<Doorplate> test2(@Param("id") String id, @Param("name") String name);
}