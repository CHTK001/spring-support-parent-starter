package com.chua.starter.spider.support.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.spider.support.domain.SpiderCredential;
import com.chua.starter.spider.support.mapper.SpiderCredentialMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 凭证池 Repository
 *
 * @author CH
 */
@Repository
public class SpiderCredentialRepository extends ServiceImpl<SpiderCredentialMapper, SpiderCredential> {

    /**
     * 查询所有凭证。
     */
    public List<SpiderCredential> findAll() {
        return list();
    }

    /**
     * 根据 ID 查询凭证。
     */
    public Optional<SpiderCredential> findById(Long id) {
        return Optional.ofNullable(getById(id));
    }

    /**
     * 保存或更新凭证。
     */
    public void save(SpiderCredential credential) {
        saveOrUpdate(credential);
    }

    /**
     * 根据 ID 删除凭证。
     */
    public void deleteById(Long id) {
        removeById(id);
    }
}
