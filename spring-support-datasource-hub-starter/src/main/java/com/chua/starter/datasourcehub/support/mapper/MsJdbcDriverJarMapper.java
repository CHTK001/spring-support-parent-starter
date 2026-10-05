package com.chua.starter.datasourcehub.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.datasourcehub.support.entity.MsJdbcDriverJar;
import org.apache.ibatis.annotations.Mapper;

/**
 * JDBC 驱动 jar 台账 Mapper。
 *
 * @author CH
 * @since 4.0.0.43
 */
@Mapper
public interface MsJdbcDriverJarMapper extends BaseMapper<MsJdbcDriverJar> {
}