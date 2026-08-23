package com.chua.starter.ai.support.nomifun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.nomifun.store.NomifunProviderConnection;
import org.apache.ibatis.annotations.Mapper;

/**
 * nomifun 提供商连接 Mapper。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface NomifunProviderConnectionMapper extends BaseMapper<NomifunProviderConnection> {
}