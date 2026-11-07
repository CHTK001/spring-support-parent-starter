package com.chua.starter.server.support.model.vo;

import java.util.List;

/**
 * 主机资产归档导入结果。
 *
 * <p>计数分成「新建」与「已存在」两档：导入要可重复执行（同一个文件点两次不能
 * 变出两套主机 / 身份），所以重复项只记 matched 不报错。真正需要人看的是
 * {@code warnings}：缺地址、身份名对不上、绑定已存在等被跳过的行都会写在这里，
 * 前端直接展示，避免「导入成功但少了半台机器」这种无声丢失。</p>
 *
 * @param hostsCreated      新建主机数
 * @param hostsMatched      命中已有主机数
 * @param identitiesCreated 新建身份数
 * @param identitiesMatched 命中已有身份数
 * @param bindingsCreated   新建绑定数
 * @param bindingsSkipped   已存在或无法解析而跳过的绑定数
 * @param warnings          逐行提示（可直接展示）
 * @author CH
 * @since 4.0.0.54
 */
public record HostArchiveImportVO(
        int hostsCreated,
        int hostsMatched,
        int identitiesCreated,
        int identitiesMatched,
        int bindingsCreated,
        int bindingsSkipped,
        List<String> warnings
) {

    /**
     * 规范构造器：提示列表做防御性拷贝。
     *
     * @param hostsCreated      新建主机数
     * @param hostsMatched      命中已有主机数
     * @param identitiesCreated 新建身份数
     * @param identitiesMatched 命中已有身份数
     * @param bindingsCreated   新建绑定数
     * @param bindingsSkipped   跳过的绑定数
     * @param warnings          逐行提示
     */
    public HostArchiveImportVO {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
