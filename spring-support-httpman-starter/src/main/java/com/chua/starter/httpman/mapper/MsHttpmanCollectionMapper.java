package com.chua.starter.httpman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.httpman.entity.MsHttpmanCollection;
import org.apache.ibatis.annotations.Mapper;

/**
 * 接口调试控制台 - 请求集合 映射器
 *
 * <p>使用 MyBatis-Plus {@link BaseMapper} 提供的能力完成全表 CRUD，
 * 树形结构由 {@link com.chua.starter.httpman.service.HttpmanServiceImpl}
 * 在内存中组装。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Mapper
public interface MsHttpmanCollectionMapper extends BaseMapper<MsHttpmanCollection> {
}
