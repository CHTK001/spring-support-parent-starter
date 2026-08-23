package com.chua.starter.ai.support.nomifun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.nomifun.store.NomifunProviderModel;
import org.apache.ibatis.annotations.Mapper;

/**
 * nomifun 提供商模型 Mapper。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface NomifunProviderModelMapper extends BaseMapper<NomifunProviderModel> {
}