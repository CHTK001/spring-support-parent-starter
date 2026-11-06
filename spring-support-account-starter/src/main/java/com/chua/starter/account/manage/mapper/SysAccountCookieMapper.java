package com.chua.starter.account.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.account.manage.store.SysAccountCookie;
import org.apache.ibatis.annotations.Mapper;

/**
 * Cookie 池映射器。
 *
 * @author CH
 * @since 4.0.0.52
 */
@Mapper
public interface SysAccountCookieMapper extends BaseMapper<SysAccountCookie> {
}
