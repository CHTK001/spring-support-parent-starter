package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiAppKey;

import java.util.List;
import java.util.Optional;

public interface SysAiAppKeyService {

    List<SysAiAppKey> list();

    SysAiAppKey save(SysAiAppKey appKey);

    boolean update(SysAiAppKey appKey);

    boolean deleteById(Integer id);

    default Optional<SysAiAppKey> findByAppKey(String appKey) {
        return Optional.ofNullable(getByAppKey(appKey));
    }

    SysAiAppKey getByAppKey(String appKey);

    Integer getGroupIdByAppKey(String appKey);

    boolean verify(String appKey, String secret, String ip);

    void updateUseInfoByAppKey(String appKey);
}
