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
    /**
     * 列表树
    */
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
                .collect(Collectors.groupingBy(vo -> vo.getKnowledgePointParentId() == null ? 0L : vo.getKnowledgePointParentId()));

        List<KnowledgePointVO> tree = new ArrayList<>();
 // 构建树形结构，根节点 父标识 为 0
        /**
         * 构建children。
         * @param 0L 0L
         * @param groupMap 群体映射
         * @param tree 树
         */
        buildChildren(0L, groupMap, tree);
        return tree;
    }

    /**
     * 递归构建子节点
     *
     * @param parentId 父节点标识
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
            /**
             * 构建children。
             * @param vo.getId( vo.获取标识(
             */
            buildChildren(vo.getKnowledgePointId(), groupMap, subChildren);
            vo.setChildren(subChildren);
            target.add(vo);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 保存知识point
     *
     * @param dto dto
     * @return 保存知识point的结果
     */
    public Long saveKnowledgePoint(KnowledgePointSaveDTO dto) {
        EduKnowledgePoint entity = new EduKnowledgePoint();
        entity.setKnowledgePointName(dto.getKnowledgePointName());
        entity.setKnowledgePointCode(dto.getKnowledgePointCode());
        entity.setKnowledgePointParentId(dto.getKnowledgePointParentId() != null ? dto.getKnowledgePointParentId() : 0L);
        entity.setKnowledgePointSubject(dto.getKnowledgePointSubject());
        entity.setKnowledgePointGradeLevel(dto.getKnowledgePointGradeLevel());
        entity.setKnowledgePointGrade(dto.getKnowledgePointGrade());
        entity.setKnowledgePointDescription(dto.getKnowledgePointDescription());
        entity.setKnowledgePointSortOrder(dto.getKnowledgePointSortOrder() != null ? dto.getKnowledgePointSortOrder() : 0);

        if (dto.getKnowledgePointId() != null && dto.getKnowledgePointId() > 0) {
            // 更新
            entity.setKnowledgePointId(dto.getKnowledgePointId());
            entity.setKnowledgePointStatus(dto.getKnowledgePointStatus() != null ? dto.getKnowledgePointStatus() : 1);
            entity.setKnowledgePointUpdateTime(LocalDateTime.now());
            baseMapper.updateById(entity);
            log.info("更新知识点成功，ID: {}", dto.getKnowledgePointId());
            return dto.getKnowledgePointId();
        } else {
            // 新增
            entity.setKnowledgePointStatus(dto.getKnowledgePointStatus() != null ? dto.getKnowledgePointStatus() : 1);
            entity.setKnowledgePointCreateTime(LocalDateTime.now());
            entity.setKnowledgePointUpdateTime(LocalDateTime.now());
            baseMapper.insert(entity);
            log.info("新增知识点成功，ID: {}", entity.getKnowledgePointId());
            return entity.getKnowledgePointId();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除知识point
     *
     * @param id 标识
     * @return 删除知识point的结果
     */
    public boolean deleteKnowledgePoint(Long id) {
        EduKnowledgePoint exists = baseMapper.selectById(id);
        if (exists == null) {
            /**
             * 业务异常。
             * @param "知识点不存在" "知识点不存在"
             * @return 业务异常的结果
             */
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
            /**
             * 删除知识point。
             * @param child.getKnowledgePointId( 子.获取知识pointid(
             */
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
        vo.setKnowledgePointId(entity.getKnowledgePointId());
        vo.setKnowledgePointName(entity.getKnowledgePointName());
        vo.setKnowledgePointCode(entity.getKnowledgePointCode());
        vo.setKnowledgePointParentId(entity.getKnowledgePointParentId());
        vo.setKnowledgePointSubject(entity.getKnowledgePointSubject());
        vo.setKnowledgePointGradeLevel(entity.getKnowledgePointGradeLevel());
        vo.setKnowledgePointGrade(entity.getKnowledgePointGrade());
        vo.setKnowledgePointDescription(entity.getKnowledgePointDescription());
        vo.setKnowledgePointSortOrder(entity.getKnowledgePointSortOrder());
        vo.setKnowledgePointStatus(entity.getKnowledgePointStatus());
        vo.setKnowledgePointCreateTime(entity.getKnowledgePointCreateTime());
        return vo;
    }
}
