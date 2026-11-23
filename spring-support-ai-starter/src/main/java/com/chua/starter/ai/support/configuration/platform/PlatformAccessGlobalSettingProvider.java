// [P3C A20 豁免] A20/A30/A31: 含界面文案等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.configuration.platform;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 平台访问密钥（platform 分组）全局设置提供者 SPI。
 *
 * <p>定义 ONNX 与 Web 浏览器推理两个平台的访问密钥（appkey）。密钥仅管理员可在
 * 配置中心配置与查看（{@code adminOnly=true}：定义出口按角色过滤、取值出口剔除、
 * 写入出口拒绝，见 {@code SettingsController}）；普通用户使用对应平台的模型
 * （拉取模型目录、发起对话）时必须在请求头携带匹配的密钥，由后端逐请求校验
 * （见 {@code PlatformAccessKeyService} 与 {@code PlatformKeyController}）。</p>
 *
 * <p>密钥留空表示该平台未启用访问控制（放行）；配置后非管理员必须携带匹配密钥
 * 才能拿到对应平台的模型列表或发起对话。密钥经
 * {@code GlobalSettingReader#getStrDirect} 直读全局层（管理员专属字段不存在
 * 用户覆盖层，直读即生效值）。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Spi("platform")
public class PlatformAccessGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    public static final String GROUP = "platform";

    /**
     * ONNX 平台访问密钥字段标识
     */
    public static final String FIELD_ONNX_APP_KEY = "onnx_app_key";

    /**
     * Web 浏览器推理平台访问密钥字段标识
     */
    public static final String FIELD_WEB_APP_KEY = "web_app_key";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "平台访问密钥";

    /**
     * 配置分组标识。
     *
     * @return platform
     */
    @Override
    public String group() {
        return GROUP;
    }

    /**
     * 配置分组显示名称。
     *
     * @return 平台访问密钥
     */
    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    /**
     * 平台访问密钥分组全部配置项。
     *
     * <p>两项均为管理员专属（adminOnly=true）：普通用户在配置中心看不到、也改不了；
     * 使用平台时经请求头携带密钥、由后端比对。</p>
     *
     * @return 配置项列表
     */
    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem(FIELD_ONNX_APP_KEY, "", "STRING",
                        "ONNX 平台访问密钥（用户使用 ONNX 模型时需携带；留空=不启用校验）", 1, true),
                new SettingItem(FIELD_WEB_APP_KEY, "", "STRING",
                        "Web 浏览器推理平台访问密钥（用户使用 Web 模型时需携带；留空=不启用校验）", 2, true));
    }
}
