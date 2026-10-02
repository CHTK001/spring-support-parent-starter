package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduKnowledgePoint;
import com.chua.starter.eduai.model.vo.KnowledgeCardVO;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识点 Mapper。
 *
 * @author CH
 */

@Mapper
public interface EduKnowledgePointMapper extends BaseMapper<EduKnowledgePoint> {

    /**
     * 知识点树形列表（XML 语句 selectTree：多条件筛选 + 仅启用状态 + 归属隔离 + 排序号与ID升序）。
     *
     * @param subject    学科，为空时不限定
     * @param gradeLevel 学段，为空时不限定
     * @param grade      年级，为空时不限定
     * @param scope      归属隔离参数
     * @return 知识点列表
     */
    List<EduKnowledgePoint> selectTree(@Param("subject") String subject,
                                       @Param("gradeLevel") String gradeLevel,
                                       @Param("grade") String grade,
                                       @Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 取父节点下处于启用状态的子知识点（XML 语句 selectEnabledChildren，级联停用用）。
     *
     * @param parentId 父知识点标识
     * @return 子知识点列表
     */
    List<EduKnowledgePoint> selectEnabledChildren(@Param("parentId") Long parentId);

    /**
     * 按关键字扁平检索启用中的知识点（远程多选下拉用）。
     *
     * @param keyword 名称/编码模糊关键字，可为空
     * @param subject 学科，为空时不限定
     * @param limit   最大返回条数
     * @param scope   归属隔离参数
     * @return 知识点列表
     */
    List<EduKnowledgePoint> selectKeyword(@Param("keyword") String keyword,
                                          @Param("subject") String subject,
                                          @Param("limit") int limit,
                                          @Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 知识卡片扁平列表（XML 语句 selectCards：带有效文本/附件计数 + 归属隔离）。
     *
     * @param keyword    名称/编码模糊关键字，可为空
     * @param subject    学科，为空时不限定
     * @param gradeLevel 学段，为空时不限定
     * @param grade      年级，为空时不限定
     * @param scope      归属隔离参数
     * @return 卡片列表（含 textCount / fileCount）
     */
    List<KnowledgeCardVO> selectCards(@Param("keyword") String keyword,
                                      @Param("subject") String subject,
                                      @Param("gradeLevel") String gradeLevel,
                                      @Param("grade") String grade,
                                      @Param("scope") EduUserContext.OwnerScope scope);
}
