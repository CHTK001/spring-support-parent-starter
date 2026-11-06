package com.chua.starter.account.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.account.manage.store.SysAccountAppKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部访问密钥映射器。
 *
 * @author CH
 * @since 4.0.0.52
 */
@Mapper
public interface SysAccountAppKeyMapper extends BaseMapper<SysAccountAppKey> {
}
