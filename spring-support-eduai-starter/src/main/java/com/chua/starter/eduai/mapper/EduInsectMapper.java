package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduInsect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 昆虫图鉴 Mapper
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduInsectMapper extends BaseMapper<EduInsect> {

    /**
     * 按所属目与变态类型获取昆虫列表
     *
     * @param order         所属目 slug，为空时不参与过滤
     * @param metamorphosis 变态类型，为空时不参与过滤
     * @param maxDifficulty 最大难度，为空时不参与过滤
     * @param count         数量
     * @param userId        归属隔离：非空时只返回系统物种与该用户的物种；管理员传空看全部
     * @return 昆虫列表
     */
    List<EduInsect> selectByFilter(@Param("order") String order,
                                   @Param("metamorphosis") String metamorphosis,
                                   @Param("maxDifficulty") Integer maxDifficulty,
                                   @Param("count") int count,
                                   @Param("userId") String userId);

    /**
     * 统计各目物种数，用于前端「目」筛选 chips 与难度推导
     *
     * @param userId 归属隔离：非空时只统计系统物种与该用户的物种
     * @return 行数据，每行含 order 与 speciesCount
     */
    List<java.util.Map<String, Object>> countByOrder(@Param("userId") String userId);

    /**
     * 取全量物种（名录用），按目与 ID 稳定排序
     *
     * <p>走自定义 XML 的 {@code SELECT *} 而不是 BaseMapper.selectList：本库的表
     * 只落了 SysBase 的 create_time/update_time，没有 create_name/create_by 等列，
     * 而 selectList 会把实体全部字段拼进 SELECT，直接报「Column create_name not found」。</p>
     *
     * @param userId 归属隔离：非空时只返回系统物种与该用户的物种
     * @return 全量物种列表
     */
    List<EduInsect> selectAll(@Param("userId") String userId);

    /**
     * 按物种标识取单条
     *
     * @param insectCode 物种英文标识（slug）
     * @return 物种，不存在时为 null
     */
    EduInsect selectByCode(@Param("insectCode") String insectCode);
}
