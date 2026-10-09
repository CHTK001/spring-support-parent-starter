package com.chua.starter.datasourcehub.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.datasourcehub.support.entity.MsNginxDashboard;
import org.apache.ibatis.annotations.Mapper;

/**
 * Nginx 自定义看板 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface MsNginxDashboardMapper extends BaseMapper<MsNginxDashboard> {
}