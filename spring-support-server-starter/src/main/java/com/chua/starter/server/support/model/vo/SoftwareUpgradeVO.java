package com.chua.starter.server.support.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 可升级软件条目 —— 「当前版本 → 仓库候选版本」。
 *
 * <p>软件管理的「升级」按钮只在有真实候选版本时出现。判断必须由包管理器给出：
 * 仅凭「仓库里存在这个包」就显示升级，会把已经是最新版的包装成「可升级」，
 * 点了以后包管理器输出 {@code already the newest version}，用户会认为按钮坏了。</p>
 *
 * @param name             软件包名
 * @param currentVersion   已安装版本（包管理器未给出时为空）
 * @param candidateVersion 仓库候选版本
 * @author CH
 * @since 4.0.0.45
 */
@Schema(description = "可升级软件条目")
public record SoftwareUpgradeVO(
        String name,
        String currentVersion,
        String candidateVersion) {
}