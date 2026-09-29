package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.eduai.model.EduPaper;
import com.chua.starter.eduai.model.dto.PaperQueryDTO;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 试卷 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduPaperMapper extends BaseMapper<EduPaper> {

    /**
     * 分页查询试卷（XML 语句 pageSelect）。
     *
     * @param page  分页参数
     * @param query 查询条件
     * @param scope 归属隔离参数
     * @return 试卷分页
     */
    IPage<EduPaper> pageSelect(Page<EduPaper> page, @Param("query") PaperQueryDTO query,
                                @Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 按试卷唯一编号取试卷（XML 语句 selectByPaperNo，生成编号时查重）。
     *
     * @param paperNo 试卷唯一编号
     * @return 试卷实体，不存在返回 null
     */
    EduPaper selectByPaperNo(@Param("paperNo") String paperNo);

    /**
     * 按主键取试卷并加行锁（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>为什么需要：改卷是「读-改-写」——读状态与题目、写状态与题项版本号。
     * 两个并发请求会同时读到同一份旧数据，随后都去改同一批
     * {@code edu_paper_question} 行，InnoDB 抛
     * 「Record has changed since last read」并冒成 500。
     * 用主键行锁把**同一张卷**的写操作串行化即可根除；不同卷之间互不阻塞。</p>
     *
     * <p>只在 {@code @Transactional} 内有效，锁随事务结束释放。</p>
     *
     * @param paperId 试卷标识
     * @return 试卷实体，不存在返回 null
     */
    EduPaper selectByIdForUpdate(@Param("paperId") Long paperId);

    /**
     * 原子地把试卷状态从期望值流转到目标值（compare-and-set）。
     *
     * @param paperId        试卷标识
     * @param expectedStatus 期望的当前状态
     * @param targetStatus   目标状态
     * @return 影响行数；1 表示本次流转成功，0 表示状态已被别人改走
     */
    int updateStatusIfCurrent(@Param("paperId") Long paperId,
                             @Param("expectedStatus") int expectedStatus,
                             @Param("targetStatus") int targetStatus);

    /**
     * 原子地归档试卷：草稿与已发布都可归档，已是归档态则不做任何事。
     *
     * @param paperId       试卷标识
     * @param targetStatus  目标状态
     * @param archivedStatus 归档态的状态值（用于排除已归档）
     * @return 影响行数；1 表示本次归档成功，0 表示已是归档态
     */
    int archiveIfNotArchived(@Param("paperId") Long paperId,
                            @Param("targetStatus") int targetStatus,
                            @Param("archivedStatus") int archivedStatus);
}
