package com.chua.starter.account.spi;

import java.util.List;

/**
 * 「从配置导入供应商」解析 SPI —— 把一种本地配置形态解析成供应商草稿清单。
 *
 * <p>渠道管理属于本模块（account-starter）；AI 控制台只是调用方。前端把用户选中的
 * 配置文件（字节）交给后端，由本 SPI 按来源分发解析，统一产出
 * {@link ChannelImportEntry}；是否创建供应商仍由前端预览确认后走既有
 * {@code /api/providers} 管理接口，本 SPI 不落库。</p>
 *
 * <p>新增来源三步：</p>
 * <ol>
 *   <li>写一个实现类，标 {@code @Spi("来源名")}（如 {@code magpie}）；</li>
 *   <li>在 {@code META-INF/extensions/com.chua.starter.account.spi.ChannelConfigImportParser}
 *       登记实现类全限定名；</li>
 *   <li>前端 {@code ConfigImportDialog} 的来源清单里加同名项。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface ChannelConfigImportParser {

    /**
     * 解析配置文件。
     *
     * @param files 上传的文件清单（字节原样，文本按需 UTF-8 解码；至少一个）
     * @return 供应商草稿清单（重名已去重；解析不出任何内容时返回空清单，不应为 null）
     * @throws Exception 文件损坏 / 形态不符时抛出，消息会透传给前端展示
     */
    List<ChannelImportEntry> parse(List<ChannelImportFile> files) throws Exception;
}
