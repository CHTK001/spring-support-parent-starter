package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiGenerateTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 生成任务 映射器。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysAiGenerateTaskMapper extends BaseMapper<SysAiGenerateTask> {

    /**
     * 找出仍把整张图以 base64 存在列里的存量行，只返回主键。
     *
     * <p>只取主键是必须的：{@code result} 列映射了 {@code JacksonTypeHandler}，
     * 走 Java 侧的条件构造会由该 handler 绑定参数导致条件永远不成立，
     * 而 XML 自动映射又不会走那个 handler。取主键后回服务层逐条 selectById，
     * 那条路径上的 handler 是正确的。详见同名 XML 里的说明。</p>
     *
     * @param pattern 待匹配的内容片段，如 {@code data:image}
     * @param limit   单次最多取多少行
     * @return 命中的任务主键
     */
    List<String> selectIdsWithInlineMedia(@Param("pattern") String pattern, @Param("limit") int limit);
}
