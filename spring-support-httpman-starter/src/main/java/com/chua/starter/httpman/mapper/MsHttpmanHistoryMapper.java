package com.chua.starter.httpman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.httpman.entity.MsHttpmanHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 接口调试控制台 - 请求历史 映射器
 *
 * <p>使用 MyBatis-Plus {@link BaseMapper} 提供的能力完成全表 CRUD，
 * 历史分页/概述由 {@link com.chua.starter.httpman.service.HttpmanServiceImpl}
 * 的 {@code LambdaQueryWrapper} 支撑。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Mapper
public interface MsHttpmanHistoryMapper extends BaseMapper<MsHttpmanHistory> {
}
