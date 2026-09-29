package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.eduai.model.EduKnowledgePoint;
import com.chua.starter.eduai.model.dto.KnowledgeFileBindDTO;
import com.chua.starter.eduai.model.dto.KnowledgePointSaveDTO;
import com.chua.starter.eduai.model.vo.KnowledgeCardVO;
import com.chua.starter.eduai.model.vo.KnowledgeFileVO;
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
     * @param id 知识点标识
     * @return 是否成功
     */
    boolean deleteKnowledgePoint(Long id);

    /**
     * 关键字扁平检索（下拉远程过滤用）
     *
     * @param keyword 名称/编码模糊关键字
     * @param subject 学科，可为空
     * @param limit   最大条数，默认 50
     * @return 知识点列表（无 children）
     */
    List<KnowledgePointVO> listByKeyword(String keyword, String subject, Integer limit);

    /**
     * 知识卡片扁平列表（带有效文本/附件计数）
     *
     * @param keyword    名称/编码模糊关键字，可为空
     * @param subject    学科，可为空
     * @param gradeLevel 学段，可为空
     * @param grade      年级，可为空
     * @return 卡片列表
     */
    List<KnowledgeCardVO> listCards(String keyword, String subject, String gradeLevel, String grade);

    /**
     * 卡片详情（含富文本正文）
     *
     * @param id 知识点标识
     * @return 知识点详情
     */
    KnowledgePointVO getDetail(Long id);

    /**
     * 卡片下附件条目列表（启用态，按排序号升序）
     *
     * @param knowledgePointId 卡片标识
     * @return 附件条目列表
     */
    List<KnowledgeFileVO> listFiles(Long knowledgePointId);

    /**
     * 把已上传附件绑定到卡片
     *
     * @param dto 附件绑定数据
     * @return 附件条目标识
     */
    Long bindFile(KnowledgeFileBindDTO dto);

    /**
     * 删除卡片附件条目（逻辑删除，不删磁盘文件）
     *
     * @param id 附件条目标识
     * @return 是否成功
     */
    boolean deleteFile(Long id);
}
