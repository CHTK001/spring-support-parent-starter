package com.chua.starter.spider.support.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.mapper.SpiderTaskMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 爬虫任务 Repository。
 *
 * <p>乐观锁由 MyBatis-Plus {@code @Version} 注解自动处理，
 * 版本冲突时 update 返回 0，调用方应抛出冲突异常。</p>
 *
 * @author CH
 */
@Repository
public class SpiderTaskRepository extends ServiceImpl<SpiderTaskMapper, SpiderTaskDefinition> {

    /**
     * 根据任务编码查询任务。
     */
    public Optional<SpiderTaskDefinition> findByTaskCode(String taskCode) {
        return Optional.ofNullable(
                getOne(new LambdaQueryWrapper<SpiderTaskDefinition>()
                        .eq(SpiderTaskDefinition::getTaskCode, taskCode))
        );
    }

    /**
     * 检查任务编码是否已存在（排除指定 ID）。
     */
    public boolean existsByTaskCode(String taskCode, Long excludeId) {
        LambdaQueryWrapper<SpiderTaskDefinition> wrapper = new LambdaQueryWrapper<SpiderTaskDefinition>()
                .eq(SpiderTaskDefinition::getTaskCode, taskCode);
        if (excludeId != null) {
            wrapper.ne(SpiderTaskDefinition::getId, excludeId);
        }
        return count(wrapper) > 0;
    }

    /**
     * 保存或更新任务，版本冲突时抛出 {@link SpiderOptimisticLockException}。
     */
    public void saveOrUpdateWithLock(SpiderTaskDefinition task) {
        if (task.getId() == null) {
            save(task);
        } else {
            boolean updated = updateById(task);
            if (!updated) {
                throw new SpiderOptimisticLockException(
                        "任务 [" + task.getId() + "] 更新失败：版本冲突，请刷新后重试");
            }
        }
    }
}
