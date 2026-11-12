package com.chua.starter.collect.service;

import com.chua.starter.collect.vo.CollectSettingVO;

import java.util.List;
import java.util.Map;

/**
 * 采集平台运行参数服务。
 *
 * <p>把原本只能写在 yml 里的 {@code plugin.collect.*} 暴露成可读可写的配置项：
 * 界面改完立即写进 {@code CollectProperties}，同时落库到 {@code collect_setting}，
 * 重启后仍生效；删除覆盖行即回到 yml 缺省值。</p>
 *
 * <p>只有「执行期读取」的参数支持热生效；{@code restartRequired=true} 的开关
 * （脚本节点、子流程节点、自定义函数）在启动注册执行器时就要定下来，必须重启。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface CollectSettingsService {

    /**
     * 全部参数项（含当前值、缺省值与是否被覆盖）。
     *
     * @return 参数项列表，按分组与声明顺序返回
     */
    List<CollectSettingVO> listSettings();

    /**
     * 保存若干参数：校验 → 写库 → 立即应用到运行期配置。
     *
     * @param values 配置键到值（值统一按字符串传，服务按定义的类型校验）
     * @return 保存后的全部参数项
     */
    List<CollectSettingVO> saveSettings(Map<String, String> values);

    /**
     * 恢复缺省：删除覆盖行并把运行期值改回 yml 缺省。
     *
     * @param keys 要恢复的配置键；为空表示全部恢复
     * @return 恢复后的全部参数项
     */
    List<CollectSettingVO> resetSettings(List<String> keys);

    /**
     * 确保数据库覆盖值已装载（幂等）。
     *
     * <p>启动期由执行器注册表先调用一次：脚本/子流程开关在注册节点时就要生效，
     * 不能等 ApplicationRunner。重复调用不会重复写属性。</p>
     */
    void initialize();
}
