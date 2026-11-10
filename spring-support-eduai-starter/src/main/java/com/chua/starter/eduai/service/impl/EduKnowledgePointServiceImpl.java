package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduKnowledgeFileMapper;
import com.chua.starter.eduai.mapper.EduKnowledgePointMapper;
import com.chua.starter.eduai.model.EduKnowledgeFile;
import com.chua.starter.eduai.model.EduKnowledgePoint;
import com.chua.starter.eduai.model.dto.KnowledgeFileBindDTO;
import com.chua.starter.eduai.model.dto.KnowledgePointSaveDTO;
import com.chua.starter.eduai.model.vo.KnowledgeCardVO;
import com.chua.starter.eduai.model.vo.KnowledgeFileVO;
import com.chua.starter.eduai.model.vo.KnowledgePointVO;
import com.chua.starter.eduai.service.IEduKnowledgePointService;
import com.chua.starter.eduai.support.util.EduUserContext;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
                public class EduKnowledgePointServiceImpl extends ServiceImpl<EduKnowledgePointMapper, EduKnowledgePoint
                    > implements IEduKnowledgePointService {

      /**
       * 附件条目 Mapper
       */
    private final EduKnowledgeFileMapper knowledgeFileMapper;

    @Override
      /**
       * 列表树
       * @param grade 字符串
       * @param gradeLevel 字符串
       * @param subject 字符串
       * @return 返回值
      */
    public List<KnowledgePointVO> listTree(String subject, String gradeLevel, String grade) {
        List<EduKnowledgePoint> list = baseMapper.selectTree(subject, gradeLevel, grade,
                EduUserContext.ownerScope());

          // 转换为 VO 列表
        List<KnowledgePointVO> voList = list.stream().map(this::toVO).collect(Collectors.toList());
          // 按父节点分组
        Map<Long, List<KnowledgePointVO>> groupMap = voList.stream()
                .collect(Collectors.groupingBy(vo -> vo.knowledgePointParentId() == null ? 0L : vo.knowledgePointParentId()));

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
            buildChildren(vo.knowledgePointId(), groupMap, subChildren);
            target.add(vo.withChildren(subChildren));
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
        entity.setKnowledgePointName(dto.knowledgePointName());
        entity.setKnowledgePointCode(dto.knowledgePointCode());
        entity.setKnowledgePointParentId(dto.knowledgePointParentId() != null ? dto.knowledgePointParentId() : 0L);
        entity.setKnowledgePointSubject(dto.knowledgePointSubject());
        entity.setKnowledgePointGradeLevel(dto.knowledgePointGradeLevel());
        entity.setKnowledgePointGrade(dto.knowledgePointGrade());
        entity.setKnowledgePointDescription(dto.knowledgePointDescription());
        entity.setKnowledgePointSortOrder(dto.knowledgePointSortOrder() != null ? dto.knowledgePointSortOrder() : 0);
        entity.setKnowledgePointContentType(dto.knowledgePointContentType() != null && !dto.knowledgePointContentType().isBlank()
                ? dto.knowledgePointContentType() : "TEXT");
        entity.setKnowledgePointContent(dto.knowledgePointContent());
        entity.setKnowledgePointFileName(dto.knowledgePointFileName());
        entity.setKnowledgePointFilePath(dto.knowledgePointFilePath());
        entity.setKnowledgePointFileType(dto.knowledgePointFileType());
        entity.setKnowledgePointFileSize(dto.knowledgePointFileSize());

        if (dto.knowledgePointId() != null && dto.knowledgePointId() > 0) {
              // 更新：校验归属并锁定原归属
            EduKnowledgePoint exists = baseMapper.selectById(dto.knowledgePointId());
            if (exists == null) {
                throw new BusinessException("知识点不存在");
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            entity.setOwnerUserId(exists.getOwnerUserId());
            entity.setKnowledgePointId(dto.knowledgePointId());
            entity.setKnowledgePointStatus(dto.knowledgePointStatus() != null ? dto.knowledgePointStatus() : 1);
            entity.setUpdateTime(LocalDateTime.now());
            baseMapper.updateById(entity);
            log.info("更新知识点成功，ID: {}", dto.knowledgePointId());
            return dto.knowledgePointId();
        } else {
              // 新增
            entity.setOwnerUserId(EduUserContext.requireUserId());
            entity.setKnowledgePointStatus(dto.knowledgePointStatus() != null ? dto.knowledgePointStatus() : 1);
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
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
        EduUserContext.checkWritable(exists.getOwnerUserId());
          // 逻辑删除：状态置为停用
        exists.setKnowledgePointStatus(0);
        exists.setUpdateTime(LocalDateTime.now());
        int rows = baseMapper.updateById(exists);

          // 级联停用子节点
        List<EduKnowledgePoint> children = baseMapper.selectEnabledChildren(id);
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

    @Override
    public List<KnowledgePointVO> listByKeyword(String keyword, String subject, Integer limit) {
        int max = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        List<EduKnowledgePoint> list = baseMapper.selectKeyword(keyword, subject, max, EduUserContext.ownerScope());
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeCardVO> listCards(String keyword, String subject, String gradeLevel, String grade) {
        return baseMapper.selectCards(keyword, subject, gradeLevel, grade, EduUserContext.ownerScope());
    }

    @Override
    public KnowledgePointVO getDetail(Long id) {
        EduKnowledgePoint card = requireCardReadable(id);
        return toVO(card);
    }

    @Override
    public List<KnowledgeFileVO> listFiles(Long knowledgePointId) {
        requireCardReadable(knowledgePointId);
        List<EduKnowledgeFile> list = knowledgeFileMapper.selectList(
                new LambdaQueryWrapper<EduKnowledgeFile>()
                        .eq(EduKnowledgeFile::getKnowledgePointId, knowledgePointId)
                        .eq(EduKnowledgeFile::getKnowledgeFileStatus, 1)
                        .orderByAsc(EduKnowledgeFile::getKnowledgeFileSortOrder)
                        .orderByAsc(EduKnowledgeFile::getKnowledgeFileId));
        return list.stream().map(this::toFileVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
      /**
       * bind File
       * @param dto 参数
       * @return 返回值
      */
    public Long bindFile(KnowledgeFileBindDTO dto) {
        if (dto.knowledgePointId() == null || dto.knowledgePointId() <= 0) {
            throw new BusinessException("缺少所属卡片标识");
        }
        if (!StringUtils.hasText(dto.knowledgeFilePath())) {
            throw new BusinessException("附件路径不能为空");
        }
        EduKnowledgePoint card = requireCardWritable(dto.knowledgePointId());

        EduKnowledgeFile entity = new EduKnowledgeFile();
        entity.setKnowledgePointId(dto.knowledgePointId());
        entity.setKnowledgeFileName(dto.knowledgeFileName());
        entity.setKnowledgeFilePath(dto.knowledgeFilePath());
        entity.setKnowledgeFileType(dto.knowledgeFileType());
        entity.setKnowledgeFileSize(dto.knowledgeFileSize());
        entity.setKnowledgeFileSortOrder(dto.knowledgeFileSortOrder() != null ? dto.knowledgeFileSortOrder() : 0);
        entity.setKnowledgeFileStatus(1);
        entity.setOwnerUserId(card.getOwnerUserId() != null ? card.getOwnerUserId() : EduUserContext.requireUserId());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        knowledgeFileMapper.insert(entity);
        log.info("绑定知识卡片附件，卡ID: {}, 附件ID: {}", dto.knowledgePointId(), entity.getKnowledgeFileId());
        return entity.getKnowledgeFileId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
      /**
       * delete File
       * @param id 长整数
       * @return 返回值
      */
    public boolean deleteFile(Long id) {
        EduKnowledgeFile exists = knowledgeFileMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException("附件条目不存在");
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        exists.setKnowledgeFileStatus(0);
        exists.setUpdateTime(LocalDateTime.now());
        int rows = knowledgeFileMapper.updateById(exists);
        log.info("删除知识卡片附件条目，ID: {}", id);
        return rows > 0;
    }

      /**
       * 校验卡片可读并返回实体
       *
       * @param knowledgePointId 卡片标识
       * @return 卡片实体
       */
    private EduKnowledgePoint requireCardReadable(Long knowledgePointId) {
        EduKnowledgePoint card = baseMapper.selectById(knowledgePointId);
        if (card == null || card.getKnowledgePointStatus() == null || card.getKnowledgePointStatus() != 1) {
            throw new BusinessException("知识点卡片不存在或已停用");
        }
        EduUserContext.checkReadable(card.getOwnerUserId());
        return card;
    }

      /**
       * 校验卡片可写并返回实体
       *
       * @param knowledgePointId 卡片标识
       * @return 卡片实体
       */
    private EduKnowledgePoint requireCardWritable(Long knowledgePointId) {
        EduKnowledgePoint card = baseMapper.selectById(knowledgePointId);
        if (card == null) {
            throw new BusinessException("知识点卡片不存在");
        }
        EduUserContext.checkWritable(card.getOwnerUserId());
        return card;
    }

      /**
       * 实体转 VO
       *
       * @param entity 实体
       * @return VO
       */
    private KnowledgePointVO toVO(EduKnowledgePoint entity) {
        KnowledgePointVO vo = new KnowledgePointVO(
                entity.getKnowledgePointId(),
                entity.getKnowledgePointName(),
                entity.getKnowledgePointCode(),
                entity.getKnowledgePointParentId(),
                entity.getKnowledgePointSubject(),
                entity.getKnowledgePointGradeLevel(),
                entity.getKnowledgePointGrade(),
                entity.getKnowledgePointDescription(),
                entity.getKnowledgePointSortOrder(),
                entity.getKnowledgePointStatus(),
                entity.getKnowledgePointContentType(),
                entity.getKnowledgePointContent(),
                entity.getKnowledgePointFileName(),
                entity.getKnowledgePointFilePath(),
                entity.getKnowledgePointFileType(),
                entity.getKnowledgePointFileSize(),
                entity.getCreateTime(),
                null
        );
        return vo;
    }

      /**
       * 附件实体转 VO
       *
       * @param entity 实体
       * @return VO
       */
    private KnowledgeFileVO toFileVO(EduKnowledgeFile entity) {
        return new KnowledgeFileVO(
                entity.getKnowledgeFileId(),
                entity.getKnowledgePointId(),
                entity.getKnowledgeFileName(),
                entity.getKnowledgeFilePath(),
                entity.getKnowledgeFileType(),
                entity.getKnowledgeFileSize(),
                entity.getKnowledgeFileSortOrder(),
                entity.getKnowledgeFileStatus(),
                entity.getCreateTime());
    }
}

