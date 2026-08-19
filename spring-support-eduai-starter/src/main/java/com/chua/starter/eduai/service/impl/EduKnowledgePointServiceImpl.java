package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduKnowledgePointMapper;
import com.chua.starter.eduai.model.EduKnowledgePoint;
import com.chua.starter.eduai.model.dto.KnowledgePointSaveDTO;
import com.chua.starter.eduai.model.vo.KnowledgePointVO;
import com.chua.starter.eduai.service.IEduKnowledgePointService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识点服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
public class EduKnowledgePointServiceImpl extends ServiceImpl<EduKnowledgePointMapper, EduKnowledgePoint> implements IEduKnowledgePointService {

    @Override
    public List<KnowledgePointVO> listTree(String subject, String gradeLevel, String grade) {
        LambdaQueryWrapper<EduKnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        // 按学科筛选
        if (StringUtils.hasText(subject)) {
            wrapper.eq(EduKnowledgePoint::getKnowledgePointSubject, subject);
        }
        // 按学段筛选
        if (StringUtils.hasText(gradeLevel)) {
            wrapper.eq(EduKnowledgePoint::getKnowledgePointGradeLevel, gradeLevel);
        }
        // 按年级筛选
        if (StringUtils.hasText(grade)) {
            wrapper.eq(EduKnowledgePoint::getKnowledgePointGrade, grade);
        }
        // 只查询启用状态
        wrapper.eq(EduKnowledgePoint::getKnowledgePointStatus, 1)
                .orderByAsc(EduKnowledgePoint::getKnowledgePointSortOrder)
                .orderByAsc(EduKnowledgePoint::getKnowledgePointId);
        List<EduKnowledgePoint> list = baseMapper.selectList(wrapper);

        // 转换为 VO 列表
        List<KnowledgePointVO> voList = list.stream().map(this::toVO).collect(Collectors.toList());
        // 按父节点分组
        Map<Long, List<KnowledgePointVO>> groupMap = voList.stream()
                .collect(Collectors.groupingBy(vo -> vo.getParentId() == null ? 0L : vo.getParentId()));

        List<KnowledgePointVO> tree = new ArrayList<>();
        // 构建树形结构，根节点 parentId 为 0
        buildChildren(0L, groupMap, tree);
        return tree;
    }

    /**
     * 递归构建子节点
     *
     * @param parentId 父节点ID
     * @param groupMap 按父节点分组的集合
     * @param target   目标集合
     */
    private void buildChildren(Long parentId, Map<Long, List<KnowledgePointVO>> groupMap, List<KnowledgePointVO> target) {
        List<KnowledgePointVO> children = groupMap.get(parentId);
        if (children == null) {
            return;
        }
        for (KnowledgePointVO vo : children) {
            List<KnowledgePointVO> subChildren = new ArrayList<>();
            buildChildren(vo.getId(), groupMap, subChildren);
            vo.setChildren(subChildren);
            target.add(vo);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveKnowledgePoint(KnowledgePointSaveDTO dto) {
        EduKnowledgePoint entity = new EduKnowledgePoint();
        entity.setKnowledgePointName(dto.getName());
        entity.setKnowledgePointCode(dto.getCode());
        entity.setKnowledgePointParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        entity.setKnowledgePointSubject(dto.getSubject());
        entity.setKnowledgePointGradeLevel(dto.getGradeLevel());
        entity.setKnowledgePointGrade(dto.getGrade());
        entity.setKnowledgePointDescription(dto.getDescription());
        entity.setKnowledgePointSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);

        if (dto.getId() != null && dto.getId() > 0) {
            // 更新
            entity.setKnowledgePointId(dto.getId());
            entity.setKnowledgePointStatus(dto.getStatus() != null ? dto.getStatus() : 1);
            entity.setKnowledgePointUpdateTime(LocalDateTime.now());
            baseMapper.updateById(entity);
            log.info("更新知识点成功，ID: {}", dto.getId());
            return dto.getId();
        } else {
            // 新增
            entity.setKnowledgePointStatus(dto.getStatus() != null ? dto.getStatus() : 1);
            entity.setKnowledgePointCreateTime(LocalDateTime.now());
            entity.setKnowledgePointUpdateTime(LocalDateTime.now());
            baseMapper.insert(entity);
            log.info("新增知识点成功，ID: {}", entity.getKnowledgePointId());
            return entity.getKnowledgePointId();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteKnowledgePoint(Long id) {
        EduKnowledgePoint exists = baseMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException("知识点不存在");
        }
        // 逻辑删除：状态置为停用
        exists.setKnowledgePointStatus(0);
        exists.setKnowledgePointUpdateTime(LocalDateTime.now());
        int rows = baseMapper.updateById(exists);

        // 级联停用子节点
        LambdaQueryWrapper<EduKnowledgePoint> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(EduKnowledgePoint::getKnowledgePointParentId, id)
                .eq(EduKnowledgePoint::getKnowledgePointStatus, 1);
        List<EduKnowledgePoint> children = baseMapper.selectList(childWrapper);
        for (EduKnowledgePoint child : children) {
            deleteKnowledgePoint(child.getKnowledgePointId());
        }
        log.info("删除知识点，ID: {}", id);
        return rows > 0;
    }

    /**
     * 实体转 VO
     *
     * @param entity 实体
     * @return VO
     */
    private KnowledgePointVO toVO(EduKnowledgePoint entity) {
        KnowledgePointVO vo = new KnowledgePointVO();
        vo.setId(entity.getKnowledgePointId());
        vo.setName(entity.getKnowledgePointName());
        vo.setCode(entity.getKnowledgePointCode());
        vo.setParentId(entity.getKnowledgePointParentId());
        vo.setSubject(entity.getKnowledgePointSubject());
        vo.setGradeLevel(entity.getKnowledgePointGradeLevel());
        vo.setGrade(entity.getKnowledgePointGrade());
        vo.setDescription(entity.getKnowledgePointDescription());
        vo.setSortOrder(entity.getKnowledgePointSortOrder());
        vo.setStatus(entity.getKnowledgePointStatus());
        vo.setCreateTime(entity.getKnowledgePointCreateTime());
        return vo;
    }
}
