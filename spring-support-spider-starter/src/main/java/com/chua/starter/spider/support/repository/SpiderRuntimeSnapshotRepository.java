package com.chua.starter.spider.support.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.spider.support.domain.SpiderRuntimeSnapshot;
import com.chua.starter.spider.support.mapper.SpiderRuntimeSnapshotMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 运行时快照 Repository。
 *
 * @author CH
 */
@Repository
public class SpiderRuntimeSnapshotRepository extends ServiceImpl<SpiderRuntimeSnapshotMapper, SpiderRuntimeSnapshot> {

    /**
     * 根据任务 ID 查询快照。
     */
    public Optional<SpiderRuntimeSnapshot> findByTaskId(Long taskId) {
        return Optional.ofNullable(
                getOne(new LambdaQueryWrapper<SpiderRuntimeSnapshot>()
                        .eq(SpiderRuntimeSnapshot::getTaskId, taskId))
        );
    }

    /**
     * 根据任务 ID 删除快照。
     */
    public void deleteByTaskId(Long taskId) {
        remove(new LambdaQueryWrapper<SpiderRuntimeSnapshot>()
                .eq(SpiderRuntimeSnapshot::getTaskId, taskId));
    }
}
