package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiReidLibrary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人脸重识别底库映射器。
 *
 * <p>只管元数据表；特征向量在 jvector 里，靠 {@code vector_id} 关联，不经本 Mapper。
 * 跨帧轨迹是同一张表按 {@code track_id} 的汇聚结果，不需要额外表。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Mapper
public interface SysAiReidLibraryMapper extends BaseMapper<SysAiReidLibrary> {
}
