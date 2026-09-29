package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.ai.rag.RagDocument;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.ai.support.configuration.rag.RagClientManager;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduKnowledgeDocMapper;
import com.chua.starter.eduai.model.EduKnowledgeDoc;
import com.chua.starter.eduai.model.dto.KnowledgeDocUploadDTO;
import com.chua.starter.eduai.model.vo.KnowledgeDocVO;
import com.chua.starter.eduai.service.IEduKnowledgeDocService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.PageBounds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库文档服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
public class EduKnowledgeDocServiceImpl extends ServiceImpl<EduKnowledgeDocMapper, EduKnowledgeDoc> implements IEduKnowledgeDocService {

    /**
     * RAG 客户端管理器（可选注入，未引入 AI 模块时自动降级为直接入库）
     */
    private final ObjectProvider<RagClientManager> ragClientManagerProvider;

    /**
     * 创建 edu知识doc服务impl 实例
     * @param ragClientManagerProvider rag客户端管理器提供者
     */
    public EduKnowledgeDocServiceImpl(ObjectProvider<RagClientManager> ragClientManagerProvider) {
        this.ragClientManagerProvider = ragClientManagerProvider;
    }

    @Override
    /**
     * Page查询
    */
    public PageResult<KnowledgeDocVO> pageQuery(EduKnowledgeDoc query, long page, long pageSize) {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        // 分页参数来自请求，必须收敛边界（pageSize 封顶，否则 pageSize=1000000 会拉全表）
        Page<EduKnowledgeDoc> pageInfo = new Page<>(PageBounds.pageNum(page), PageBounds.pageSize(pageSize));
        IPage<EduKnowledgeDoc> result = baseMapper.pageSelect(pageInfo, query, scope);
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
    /**
     * uploaddoc
     *
     * @param dto dto
     * @return uploadDoc的结果
     */
    public Long uploadDoc(KnowledgeDocUploadDTO dto) {
        if (!StringUtils.hasText(dto.filePath())) {
            /**
             * 业务异常。
             * @param "文件路径不能为空" "文件路径不能为空"
             * @return 业务异常的结果
             */
            throw new BusinessException("文件路径不能为空");
        }
        EduKnowledgeDoc entity = new EduKnowledgeDoc();
        entity.setOwnerUserId(EduUserContext.requireUserId());
        entity.setKnowledgeDocName(dto.name());
        entity.setKnowledgeDocType(dto.type());
        entity.setKnowledgeDocFilePath(dto.filePath());
        entity.setKnowledgeDocFileSize(dto.fileSize());
        entity.setKnowledgeDocSubject(dto.subject());
        entity.setKnowledgeDocGradeLevel(dto.gradeLevel());
        // 调用 RAG 向量化服务进行文档切片与向量存储（可选，未配置 RAG 时直接入库）
        String vectorStoreId = vectorize(dto);
        entity.setKnowledgeDocVectorStoreId(vectorStoreId);
        entity.setKnowledgeDocStatus(vectorStoreId != null ? 1 : 0); // 1=已索引, 0=未索引
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(entity);
        log.info("上传知识库文档成功，ID: {}, 名称: {}, 向量化: {}", entity.getKnowledgeDocId(), dto.name(), vectorStoreId != null);
        return entity.getKnowledgeDocId();
    }

    /**
     * 调用 RAG 向量化服务对文档进行切片与向量存储。
     *
     * @param dto 上传文档信息
     * @return 向量存储ID，未接入 RAG 或向量化失败时返回 空
     */
    private String vectorize(KnowledgeDocUploadDTO dto) {
        RagClientManager manager = ragClientManagerProvider.getIfAvailable();
        if (manager == null) {
            log.info("[EduKnowledgeDoc] 未引入 AI/RAG 模块，跳过向量化");
            return null;
        }
        RagClient ragClient = manager.current();
        if (ragClient == null) {
            log.warn("[EduKnowledgeDoc] RAG 客户端不可用，跳过向量化");
            return null;
        }
        byte[] content = readFile(dto.filePath());
        if (content == null || content.length == 0) {
            log.warn("[EduKnowledgeDoc] 文件读取为空，跳过向量化: {}", dto.filePath());
            return null;
        }
        try {
            RagDocument document = ragClient.uploadDocument(
                    StringUtils.hasText(dto.name()) ? dto.name() : dto.filePath(), content);
            if (document != null) {
                return document.id();
            }
        } catch (Exception e) {
            log.warn("[EduKnowledgeDoc] 向量化失败，跳过: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 读取文件字节（支持本地路径与 http(s) URL）。
     *
     * @param path 文件路径或 URL
     * @return 文件字节，读取失败返回 空
     */
    private byte[] readFile(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        try {
            String lower = path.toLowerCase();
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                /**
                 * 尝试。
                 * @param URI.create(path URI.创建(路径
                 */
                try (InputStream in = URI.create(path).toURL().openStream()) {
                    return in.readAllBytes();
                }
            }
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            log.warn("[EduKnowledgeDoc] 读取文件失败: {} -> {}", path, e.getMessage());
            return null;
        }
    }

    @Override
    /**
     * 搜索
    */
    public PageResult<KnowledgeDocVO> search(String keyword, String subject, int limit) {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        List<EduKnowledgeDoc> list = baseMapper.selectSearch(keyword, subject, limit > 0 ? limit : 10, scope);
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
    /**
     * 删除Doc
     *
     * @param id 标识
     * @return 删除doc的结果
     */
    public boolean deleteDoc(Long id) {
        EduKnowledgeDoc exists = baseMapper.selectById(id);
        if (exists == null) {
            /**
             * 业务异常。
             * @param "文档不存在" "文档不存在"
             * @return 业务异常的结果
             */
            throw new BusinessException("文档不存在");
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        // 逻辑删除：状态置为失败
        exists.setKnowledgeDocStatus(2);
        exists.setUpdateTime(LocalDateTime.now());
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
        KnowledgeDocVO vo = new KnowledgeDocVO(
                entity.getKnowledgeDocId(),
                entity.getKnowledgeDocName(),
                entity.getKnowledgeDocType(),
                entity.getKnowledgeDocFilePath(),
                entity.getKnowledgeDocFileSize(),
                entity.getKnowledgeDocVectorStoreId(),
                entity.getKnowledgeDocSubject(),
                entity.getKnowledgeDocGradeLevel(),
                entity.getKnowledgeDocStatus(),
                entity.getCreateTime()
        );
        return vo;
    }
}

