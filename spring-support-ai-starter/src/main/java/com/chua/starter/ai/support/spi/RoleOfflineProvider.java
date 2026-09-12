package com.chua.starter.ai.support.spi;

import java.util.List;
import java.util.Map;

/**
* 角色（人设）离线 Provider SPI。
*
* <p>每个本地/内置角色来源一个实现（如数据库内置角色、本地文件），通过
* {@code @Spi("source-key")} 注册。前端「角色」设置页枚举全部 SPI 渲染来源
* 配置列表（每个源一个开关），开关状态持久化在 {@code sys_ai_global_setting}
* 的 {@code roles} 分组。</p>
*
* @since 4.0.0.43
* @author CH
 */
public interface RoleOfflineProvider {

    /**
    * 离线源名称（用于前端展示与开关 key）。
    *
    * @return 源名称
     */
    String name();

    /**
    * 列出该来源提供的角色列表。
    *
    * @return 角色列表（每项为可序列化 Map，含 code/name/description 等）
     */
    List<Map<String, Object>> listRoles();
}
