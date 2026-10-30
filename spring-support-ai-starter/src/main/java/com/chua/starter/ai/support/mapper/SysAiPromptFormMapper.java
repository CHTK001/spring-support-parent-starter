package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiPromptForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 提示词形态定义映射器。
 *
 * @author CH
 */
@Mapper
public interface SysAiPromptFormMapper extends BaseMapper<SysAiPromptForm> {

    /**
     * 查出指定用户下某个 key 的形态行，<b>包含已逻辑删除的行</b>。
     *
     * <p>{@code @TableLogic} 只对 MyBatis-Plus 生成的语句生效，自定义 SQL 不受影响，
     * 所以这里能捞到软删除记录。唯一键 {@code (user_id, form_key)} 不含删除标记列，
     * 软删除的行依然占着 key——复用同名形态时必须先复活它，否则插入必然撞唯一键。</p>
     *
     * <p>列名显式取别名：这张表的主键/状态/删除标记都带表前缀，{@code SELECT *}
     * 的自动映射对不上 {@code id} 这类属性名。</p>
     *
     * @param userId  归属用户
     * @param formKey 形态标识
     * @return 形态行；不存在返回 null
     */
    SysAiPromptForm selectIncludingDeleted(@Param("userId") String userId, @Param("formKey") String formKey);

    /**
     * 复活一条已逻辑删除的形态行：改回展示信息并清掉删除标记。
     *
     * <p>用显式 SQL 而不是 {@code updateById}：逻辑删除字段在 MP 的更新语句里
     * 行为不够直观，显式写出来更省心。</p>
     *
     * @param id          行主键
     * @param name        中文名
     * @param icon        图标标识
     * @param description 说明
     * @param sort        排序
     * @return 影响行数
     */
    int revive(@Param("id") Integer id, @Param("name") String name, @Param("icon") String icon,
                @Param("description") String description, @Param("sort") Integer sort);
}
