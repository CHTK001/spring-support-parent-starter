package com.chua.starter.account.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.account.manage.store.SysAccountCreditConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 上游余额查询配置映射器。
 *
 * <p>表 {@code sys_account_credit_config} 与账号表同库；主键是网关里的 upstream id，
 * 一个上游最多一条配置。</p>
 *
 * @author CH
 * @since 4.0.0.82
 */
@Mapper
public interface SysAccountCreditConfigMapper extends BaseMapper<SysAccountCreditConfig> {
}
