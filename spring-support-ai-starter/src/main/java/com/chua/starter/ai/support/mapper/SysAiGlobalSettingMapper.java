package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
* AI 全局设置 映射器。
*
* @since 4.0.0.42
* @author CH
 */
@Mapper
public interface SysAiGlobalSettingMapper extends BaseMapper<SysAiGlobalSetting> {

    /**
    * 按分组查询全部全局设置项。
    *
    * @param group 分组
    * @return 设置项列表
    * @param @Param("group" @参数("群体"
    */
    List<SysAiGlobalSetting> selectByGroup(@Param("group") String group);

    /**
    * 按分组与字段名查询设置项。
    *
    * @param group 分组
    * @param field 字段名
    * @return 设置项（可选）
    * @param @Param("group" @参数("群体"
    */
    Optional<SysAiGlobalSetting> selectByGroupAndField(@Param("group") String group, @Param("field") String field);
}
