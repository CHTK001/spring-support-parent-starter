package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerKey;

import java.util.List;

/**
 * SSH 密钥库服务
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ServerKeyService {

    /**
     * 新增密钥（私钥与口令加密落库）
     *
     * @param key 密钥
     * @return 结果
     */
    ReturnResult<MsServerKey> create(MsServerKey key);

    /**
     * 更新密钥（私钥/口令为空串时表示清空，空 表示保持不变）
     *
     * @param id  主键
     * @param key 密钥
     * @return 结果
     */
    ReturnResult<MsServerKey> update(Long id, MsServerKey key);

    /**
     * 删除密钥（被身份引用时拒绝）
     *
     * @param id 主键
     * @return 结果
     */
    ReturnResult<Void> delete(Long id);

/**
 * ѯԿб˽Կ
 *
 * @return Կб
 */
    ReturnResult<List<MsServerKey>> list();

    /**
     * ɲ濢浽Կ⣺ RSA/ED25519/ECDSA ԿԣԿ汾洢Կ
     *
     * @param name     Կ
     * @param type     ԿͣRSA / ED25519 / ECDSA
     * @param password Կڣɿ
     * @param remark   עɿ
     * @return ԿϢֻԿ
     */
    ReturnResult<MsServerKey> generate(String name, String type, String password, String remark);
}
