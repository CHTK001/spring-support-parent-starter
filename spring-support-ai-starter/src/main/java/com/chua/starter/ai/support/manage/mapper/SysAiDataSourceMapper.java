package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiDataSource;
import org.apache.ibatis.annotations.Mapper;

/**
   * 需求数据源 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiDataSourceMapper extends BaseMapper<SysAiDataSource> {
}
