package com.chua.starter.server.support.acme.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.server.support.acme.entity.MonitorAcmeAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * ACME 账户 映射器，提供 监控_acme_账户 表的基础增删改查能力。
 *
 * @since 2025-10-17
 * @author CH
 */
@Mapper
public interface MonitorAcmeAccountMapper extends BaseMapper<MonitorAcmeAccount> {
}
