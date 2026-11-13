package com.chua.starter.account.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.account.manage.store.SysAccountSecret;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账号密钥库映射器。
 *
 * <p>表 {@code sys_account_secret} 与账号表同库，Bean 名用 {@code SysAccountSecretMapper}
 * 避免与其它模块可能存在的同名 Mapper 冲突。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Mapper
public interface SysAccountSecretMapper extends BaseMapper<SysAccountSecret> {
}
