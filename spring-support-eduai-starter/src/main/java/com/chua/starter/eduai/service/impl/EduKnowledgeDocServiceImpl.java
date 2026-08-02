package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduKnowledgeDocMapper;
import com.chua.starter.eduai.model.EduKnowledgeDoc;
import com.chua.starter.eduai.model.dto.KnowledgeDocUploadDTO;
import com.chua.starter.eduai.model.vo.KnowledgeDocVO;
import com.chua.starter.eduai.service.IEduKnowledgeDocService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库文档服务实现
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class EduKnowledgeDocServiceImpl extends ServiceImpl<EduKnowledgeDocMapper, EduKnowledgeDoc> implements IEduKnowledgeDocService {

    @Override
    public PageResult<KnowledgeDocVO> pageQuery(EduKnowledgeDoc query, long page, long pageSize) {
        LambdaQueryWrapper<EduKnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            // 按文档名称筛选（like 匹配）
            if (StringUtils.hasText(query.getKnowledgeDocName())) {
                wrapper.like(EduKnowledgeDoc::getKnowledgeDocName, query.getKnowledgeDocName());
            }
            // 按学科筛选
            if (StringUtils.hasText(query.getKnowledgeDocSubject())) {
                wrapper.eq(EduKnowledgeDoc::getKnowledgeDocSubject, query.getKnowledgeDocSubject());
            }
            // 按状态筛选
            if (query.getKnowledgeDocStatus() != null) {
                wrapper.eq(EduKnowledgeDoc::getKnowledgeDocStatus, query.getKnowledgeDocStatus());
            }
        }
        // 默认按创建时间倒序
        wrapper.orderByDesc(EduKnowledgeDoc::getKnowledgeDocCreateTime);

        Page<EduKnowledgeDoc> pageInfo = new Page<>(page, pageSize);
        IPage<EduKnowledgeDoc> result = baseMapper.selectPage(pageInfo, wrapper);
        List<KnowledgeDocVO> voList = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.<KnowledgeDocVO>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(voList)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadDoc(KnowledgeDocUploadDTO dto) {
        if (!StringUtils.hasText(dto.getFilePath())) {
            throw new BusinessException("文件路径不能为空");
        }
        EduKnowledgeDoc entity = new EduKnowledgeDoc();
        entity.setKnowledgeDocName(dto.getName());
        entity.setKnowledgeDocType(dto.getType());
        entity.setKnowledgeDocFilePath(dto.getFilePath());
        entity.setKnowledgeDocFileSize(dto.getFileSize());
        entity.setKnowledgeDocSubject(dto.getSubject());
        entity.setKnowledgeDocGradeLevel(dto.getGradeLevel());
        // TODO: 调用向量化服务进行文档切片与向量存储
        entity.setKnowledgeDocStatus(1); // 已索引
        entity.setKnowledgeDocCreateTime(LocalDateTime.now());
        entity.setKnowledgeDocUpdateTime(LocalDateTime.now());
        baseMapper.insert(entity);
        log.info("上传知识库文档成功，ID: {}, 名称: {}", entity.getKnowledgeDocId(), dto.getName());
        return entity.getKnowledgeDocId();
    }

    @Override
    public PageResult<KnowledgeDocVO> search(String keyword, String subject, int limit) {
        LambdaQueryWrapper<EduKnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        // 按关键字匹配文档名称
        if (StringUtils.hasText(keyword)) {
            wrapper.like(EduKnowledgeDoc::getKnowledgeDocName, keyword);
        }
        // 按学科筛选
        if (StringUtils.hasText(subject)) {
            wrapper.eq(EduKnowledgeDoc::getKnowledgeDocSubject, subject);
        }
        // 只查询已索引文档
        wrapper.eq(EduKnowledgeDoc::getKnowledgeDocStatus, 1)
                .orderByDesc(EduKnowledgeDoc::getKnowledgeDocCreateTime)
                .last("limit " + (limit > 0 ? limit : 10));
        List<EduKnowledgeDoc> list = baseMapper.selectList(wrapper);
        List<KnowledgeDocVO> voList = list.stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.<KnowledgeDocVO>builder()
                .pageNo(1)
                .pageSize(voList.size())
                .totalPages(1)
                .total(voList.size())
                .data(voList)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDoc(Long id) {
        EduKnowledgeDoc exists = baseMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException("文档不存在");
        }
        // 逻辑删除：状态置为失败
        exists.setKnowledgeDocStatus(2);
        exists.setKnowledgeDocUpdateTime(LocalDateTime.now());
        int rows = baseMapper.updateById(exists);
        log.info("删除知识库文档，ID: {}", id);
        return rows > 0;
    }

    /**
     * 实体转 VO
     *
     * @param entity 实体
     * @return VO
     */
    private KnowledgeDocVO toVO(EduKnowledgeDoc entity) {
        KnowledgeDocVO vo = new KnowledgeDocVO();
        vo.setId(entity.getKnowledgeDocId());
        vo.setName(entity.getKnowledgeDocName());
        vo.setType(entity.getKnowledgeDocType());
        vo.setFilePath(entity.getKnowledgeDocFilePath());
        vo.setFileSize(entity.getKnowledgeDocFileSize());
        vo.setVectorStoreId(entity.getKnowledgeDocVectorStoreId());
        vo.setSubject(entity.getKnowledgeDocSubject());
        vo.setGradeLevel(entity.getKnowledgeDocGradeLevel());
        vo.setStatus(entity.getKnowledgeDocStatus());
        vo.setCreateTime(entity.getKnowledgeDocCreateTime());
        return vo;
    }
}
