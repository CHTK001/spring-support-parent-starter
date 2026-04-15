package com.chua.starter.spider.support.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.spider.support.domain.SpiderJobBinding;
import com.chua.starter.spider.support.mapper.SpiderJobBindingMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 任务调度绑定 Repository。
 *
 * @author CH
 */
@Repository
public class SpiderJobBindingRepository extends ServiceImpl<SpiderJobBindingMapper, SpiderJobBinding> {

    /**
     * 根据任务 ID 查询有效绑定。
     */
    public Optional<SpiderJobBinding> findActiveByTaskId(Long taskId) {
        return Optional.ofNullable(
                getOne(new LambdaQueryWrapper<SpiderJobBinding>()
                        .eq(SpiderJobBinding::getTaskId, taskId)
                        .eq(SpiderJobBinding::getActive, true)
                        .last("LIMIT 1"))
        );
    }

    /**
     * 根据任务 ID 删除所有绑定记录。
     */
    public void deleteByTaskId(Long taskId) {
        remove(new LambdaQueryWrapper<SpiderJobBinding>()
                .eq(SpiderJobBinding::getTaskId, taskId));
    }
}
