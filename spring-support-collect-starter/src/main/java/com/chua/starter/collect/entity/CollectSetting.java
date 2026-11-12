package com.chua.starter.collect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集平台运行参数（界面覆盖值）。
 *
 * <p>缺省值仍以 yml 的 {@code plugin.collect.*} 为准，本表只存用户在界面上改过的键；
 * 删除一行即回到 yml 缺省。启动时由 {@code CollectSettingsService} 读回并写进
 * {@code CollectProperties}，因此运行期读取参数的代码不需要改。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@TableName("collect_setting")
public class CollectSetting {

    /**
     * 主键ID。
     */
    @TableId(value = "collect_setting_id", type = IdType.AUTO)
    private Long collectSettingId;

    /**
     * 配置键（{@code plugin.collect.*} 的短名，如 {@code task-timeout-ms}）。
     */
    private String collectSettingKey;

    /**
     * 配置值，统一按字符串存储，由服务按定义的类型转换。
     */
    private String collectSettingValue;

    /**
     * 创建人姓名。
     */
    private String createName;

    /**
     * 创建人ID。
     */
    private Integer createBy;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 更新人姓名。
     */
    private String updateName;

    /**
     * 更新人ID。
     */
    private Integer updateBy;
}
