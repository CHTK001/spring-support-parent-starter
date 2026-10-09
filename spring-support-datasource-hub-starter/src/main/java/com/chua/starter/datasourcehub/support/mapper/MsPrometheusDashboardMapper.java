package com.chua.starter.datasourcehub.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.datasourcehub.support.entity.MsPrometheusDashboard;
import org.apache.ibatis.annotations.Mapper;

/**
 * Prometheus 自定义看板 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface MsPrometheusDashboardMapper extends BaseMapper<MsPrometheusDashboard> {
}
