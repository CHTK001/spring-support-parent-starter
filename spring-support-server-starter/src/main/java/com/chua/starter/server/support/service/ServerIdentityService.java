package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerIdentity;

import java.util.List;

/**
 * 登录身份服务
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ServerIdentityService {

    /**
     * 新增身份（密码加密落库）
     *
     * @param identity 身份
     * @return 结果
     */
    ReturnResult<MsServerIdentity> create(MsServerIdentity identity);

    /**
      * 更新身份（密码空串表示清空，空 表示不变）
     *
     * @param id       主键
     * @param identity 身份
     * @return 结果
     */
    ReturnResult<MsServerIdentity> update(Long id, MsServerIdentity identity);

    /**
     * 删除身份（同时解除主机绑定）
     *
     * @param id 主键
     * @return 结果
     */
    ReturnResult<Void> delete(Long id);

    /**
     * 查询身份列表（脱敏）
     *
     * @return 身份列表
     */
    ReturnResult<List<MsServerIdentity>> list();
}
