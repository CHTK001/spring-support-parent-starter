package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.eduai.model.EduKnowledgePoint;
import com.chua.starter.eduai.model.dto.KnowledgePointSaveDTO;
import com.chua.starter.eduai.model.vo.KnowledgePointVO;

import java.util.List;

/**
 * 知识点服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduKnowledgePointService extends IService<EduKnowledgePoint> {

    /**
     * 查询知识点树
     *
     * @param subject    学科
     * @param gradeLevel 学段
     * @param grade      年级
     * @return 知识点树
     */
    List<KnowledgePointVO> listTree(String subject, String gradeLevel, String grade);

    /**
     * 保存知识点（新增或更新）
     *
     * @param dto 知识点数据
     * @return 知识点ID
     */
    Long saveKnowledgePoint(KnowledgePointSaveDTO dto);

    /**
     * 删除知识点（含子节点）
     *
     * @param id 知识点ID
     * @return 是否成功
     */
    boolean deleteKnowledgePoint(Long id);
}
