package com.chua.starter.spider.support.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.spider.support.domain.SpiderWorkbenchTab;
import com.chua.starter.spider.support.mapper.SpiderWorkbenchTabMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SpiderWorkbenchTabRepository extends ServiceImpl<SpiderWorkbenchTabMapper, SpiderWorkbenchTab> {

    public List<SpiderWorkbenchTab> findAllOrdered() {
        return list(new LambdaQueryWrapper<SpiderWorkbenchTab>()
                .orderByAsc(SpiderWorkbenchTab::getSortOrder));
    }
}
