package com.chua.spider.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.spider.support.dto.SpiderDefinitionSaveRequest;
import com.chua.spider.support.entity.SpiderDataRecord;
import com.chua.spider.support.entity.SpiderDefinition;
import com.chua.spider.support.entity.SpiderExecutionRecord;
import com.chua.spider.support.mapper.SpiderDataRecordMapper;
import com.chua.spider.support.mapper.SpiderDefinitionMapper;
import com.chua.spider.support.mapper.SpiderExecutionRecordMapper;
import com.chua.spider.support.service.SpiderCenterService;
import com.chua.spider.support.vo.SpiderDataRecordVo;
import com.chua.spider.support.vo.SpiderDefinitionVo;
import com.chua.spider.support.vo.SpiderExecutionRecordVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 爬虫中心业务服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderCenterServiceImpl implements SpiderCenterService {

    /**
     * 爬虫定义数据访问接口
     */
    private final SpiderDefinitionMapper definitionMapper;

    /**
     * 采集数据数据访问接口
     */
    private final SpiderDataRecordMapper dataRecordMapper;

    /**
     * 执行记录数据访问接口
     */
    private final SpiderExecutionRecordMapper executionRecordMapper;

    /**
     * 分页查询爬虫定义。
     *
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @param keyword 编码或名称模糊匹配
     * @return 分页结果
     */
    @Override
    public Page<SpiderDefinitionVo> pageDefinitions(int pageNo, int pageSize, String keyword) {
        Page<SpiderDefinition> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SpiderDefinition> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SpiderDefinition::getSpiderCode, keyword)
                    .or().like(SpiderDefinition::getSpiderName, keyword));
        }
        wrapper.orderByDesc(SpiderDefinition::getUpdateTime);
        Page<SpiderDefinition> result = definitionMapper.selectPage(page, wrapper);
        Page<SpiderDefinitionVo> voPage = new Page<>(pageNo, pageSize, result.getTotal());
        List<SpiderDefinitionVo> vos = result.getRecords().stream()
                .map(SpiderDefinitionVo::from)
                .collect(Collectors.toList());
        voPage.setRecords(vos);
        return voPage;
    }

    /**
     * 根据编码查询定义。
     *
     * @param spiderCode 爬虫编码
     * @return 定义视图
     */
    @Override
    public SpiderDefinitionVo getDefinition(String spiderCode) {
        LambdaQueryWrapper<SpiderDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderDefinition::getSpiderCode, spiderCode);
        return SpiderDefinitionVo.from(definitionMapper.selectOne(wrapper));
    }

    /**
     * 保存或更新定义，编码存在则更新，否则新增。
     *
     * @param req 保存请求
     * @return 持久化后的视图
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpiderDefinitionVo saveDefinition(SpiderDefinitionSaveRequest req) {
        SpiderDefinition existing = findByCode(req.getSpiderCode());
        SpiderDefinition target = existing == null ? new SpiderDefinition() : existing;
        BeanUtils.copyProperties(req, target);
        if (existing == null) {
            definitionMapper.insert(target);
        } else {
            definitionMapper.updateById(target);
        }
        log.info("保存爬虫定义: code={}, depth={}, maxPages={}",
                target.getSpiderCode(), target.getSpiderMaxDepth(), target.getSpiderMaxPages());
        return SpiderDefinitionVo.from(target);
    }

    /**
     * 删除爬虫定义。
     *
     * @param spiderCode 爬虫编码
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDefinition(String spiderCode) {
        SpiderDefinition existed = findByCode(spiderCode);
        if (existed == null) {
            return false;
        }
        int rows = definitionMapper.deleteById(existed.getSpiderId());
        return rows > 0;
    }

    /**
     * 切换启停状态。
     *
     * @param spiderCode 爬虫编码
     * @param newStatus 新状态
     * @return 是否成功
     */
    @Override
    public boolean toggleStatus(String spiderCode, Integer newStatus) {
        SpiderDefinition existed = findByCode(spiderCode);
        if (existed == null) {
            return false;
        }
        existed.setSpiderStatus(newStatus);
        int rows = definitionMapper.updateById(existed);
        return rows > 0;
    }

    /**
     * 分页查询采集数据。
     *
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @param spiderDefinitionCode 爬虫编码
     * @param spiderExecutionNo 执行批次号
     * @param depth 深度过滤
     * @return 分页结果
     */
    @Override
    public Page<SpiderDataRecordVo> pageDataRecords(
            int pageNo,
            int pageSize,
            String spiderDefinitionCode,
            String spiderExecutionNo,
            Integer depth
    ) {
        Page<SpiderDataRecord> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SpiderDataRecord> wrapper = new LambdaQueryWrapper<>();
        if (spiderDefinitionCode != null && !spiderDefinitionCode.isBlank()) {
            wrapper.eq(SpiderDataRecord::getSpiderDefinitionCode, spiderDefinitionCode);
        }
        if (spiderExecutionNo != null && !spiderExecutionNo.isBlank()) {
            wrapper.eq(SpiderDataRecord::getSpiderExecutionNo, spiderExecutionNo);
        }
        if (depth != null) {
            wrapper.eq(SpiderDataRecord::getSpiderDepth, depth);
        }
        wrapper.orderByDesc(SpiderDataRecord::getCreateTime);
        Page<SpiderDataRecord> result = dataRecordMapper.selectPage(page, wrapper);
        Page<SpiderDataRecordVo> voPage = new Page<>(pageNo, pageSize, result.getTotal());
        List<SpiderDataRecordVo> vos = result.getRecords().stream()
                .map(SpiderDataRecordVo::from)
                .collect(Collectors.toList());
        voPage.setRecords(vos);
        return voPage;
    }

    /**
     * 分页查询执行记录。
     *
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @param spiderDefinitionCode 爬虫编码
     * @return 分页结果
     */
    @Override
    public Page<SpiderExecutionRecordVo> pageExecutions(
            int pageNo,
            int pageSize,
            String spiderDefinitionCode
    ) {
        Page<SpiderExecutionRecord> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SpiderExecutionRecord> wrapper = new LambdaQueryWrapper<>();
        if (spiderDefinitionCode != null && !spiderDefinitionCode.isBlank()) {
            wrapper.eq(SpiderExecutionRecord::getSpiderDefinitionCode, spiderDefinitionCode);
        }
        wrapper.orderByDesc(SpiderExecutionRecord::getCreateTime);
        Page<SpiderExecutionRecord> result = executionRecordMapper.selectPage(page, wrapper);
        Page<SpiderExecutionRecordVo> voPage = new Page<>(pageNo, pageSize, result.getTotal());
        List<SpiderExecutionRecordVo> vos = result.getRecords().stream()
                .map(SpiderExecutionRecordVo::from)
                .collect(Collectors.toList());
        voPage.setRecords(vos);
        return voPage;
    }

    /**
     * 根据编码查询定义实体（内部使用）。
     *
     * @param spiderCode 爬虫编码
     * @return 实体
     */
    private SpiderDefinition findByCode(String spiderCode) {
        if (spiderCode == null || spiderCode.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<SpiderDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderDefinition::getSpiderCode, spiderCode);
        wrapper.last("LIMIT 1");
        return definitionMapper.selectOne(wrapper);
    }
}