package com.chua.starter.httpman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.httpman.entity.MsHttpmanRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 接口调试控制台 - 请求 映射器
 *
 * <p>使用 MyBatis-Plus {@link BaseMapper} 提供的能力完成全表 CRUD，
 * 集合内排序/查询由 {@link com.chua.starter.httpman.service.HttpmanServiceImpl}
 * 的 {@code LambdaQueryWrapper} 支撑。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Mapper
public interface MsHttpmanRequestMapper extends BaseMapper<MsHttpmanRequest> {
}
