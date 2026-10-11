package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiImageLibrary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图像底库映射器。
 *
 * <p>只管元数据表；向量在 jvector 里，靠 {@code vector_id} 关联，不经本 Mapper。</p>
 *
 * @since 4.0.0.44
 * @author CH
 */
@Mapper
public interface SysAiImageLibraryMapper extends BaseMapper<SysAiImageLibrary> {
}
