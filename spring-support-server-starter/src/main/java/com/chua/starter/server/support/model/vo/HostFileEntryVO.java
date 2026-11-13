package com.chua.starter.server.support.model.vo;

/**
 * 主机文件条目。
 *
 * <p>key 是相对存储根的路径，前端所有操作（进入 / 下载 / 重命名 / 删除）都只用它；
 * {@code hostPath} 只在本机存储时才有值，用来把文件交给发布升级做投放。</p>
 *
 * @param name         条目名称（不含目录）
 * @param key          相对存储根的 key
 * @param parent       所在目录 key（根目录为空串）
 * @param size         字节数；目录为 0
 * @param directory    是否目录
 * @param lastModified 最后修改时间（毫秒）
 * @param kind         类型（folder / package / archive / image / text / code / media / other）
 * @param ext          小写扩展名（无扩展名为空串）
 * @param hostPath     本机绝对路径；非本地存储或目录为 null
 * @param md5          已登记的 MD5（上传时服务端复核入库）；无记录为 null
 * @author CH
 * @since 4.0.0.62
 */
public record HostFileEntryVO(
        String name,
        String key,
        String parent,
        long size,
        boolean directory,
        long lastModified,
        String kind,
        String ext,
        String hostPath,
        String md5
) {
}
