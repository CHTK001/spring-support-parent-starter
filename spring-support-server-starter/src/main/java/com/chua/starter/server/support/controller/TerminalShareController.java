package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.share.TerminalShareStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 终端共享接口（{@code /ms/server/terminal/share}）。
 *
 * <p>语义与文件管理协同（{@code /v2/file/collab}）<b>完全对齐</b>：一条共享记录持有
 * 发起者、可见性（PUBLIC / PRIVATE）与被授权用户集合，前端据此决定当前账号是
 * 「可协同操作」还是「只读」。唯一的差别是协同对象换成一条 SSH 终端会话，
 * 因此额外携带房间号 {@code roomId} —— 被邀请方凭它接入<b>同一条</b> SSH 连接与
 * 远端伪终端，而不是另开一条。</p>
 *
 * <p>用户身份沿用协同侧口径：由前端显式传入 {@code userId}（内部登录态功能，
 * 前端从用户 store / 框架 storage 取当前用户标识）。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Tag(name = "服务器资产-终端共享", description = "邀请用户协同操作同一终端（口径同文件协同）")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/terminal/share")
@RequiredArgsConstructor
public class TerminalShareController {

    /**
     * 终端共享存储器
     */
    private final TerminalShareStore shareStore;

    /**
     * 发起共享（同一房间重复发起时更新可见性与授权用户）。
     *
     * @param hostId     主机标识
     * @param hostName   主机名（展示用）
     * @param roomId     终端房间号（前端生成；空则服务端生成）
     * @param userId     发起者用户标识
     * @param visibility 可见性：public / private
     * @param userIds    私有时的授权用户（逗号分隔；公开时忽略）
     * @return 共享记录
     */
    @Operation(summary = "发起终端共享")
    @PostMapping("/create")
    public ReturnResult<Map<String, Object>> create(
            @RequestParam("hostId") Long hostId,
            @RequestParam(value = "hostName", required = false, defaultValue = "") String hostName,
            @RequestParam(value = "roomId", required = false, defaultValue = "") String roomId,
            @RequestParam(value = "userId", required = false, defaultValue = "anonymous") String userId,
            @RequestParam(value = "visibility", required = false, defaultValue = "public") String visibility,
            @RequestParam(value = "userIds", required = false, defaultValue = "") String userIds) {
        String owner = blankTo(userId, "anonymous");
        TerminalShareStore.TerminalShare share = shareStore.create(hostId, hostName, roomId, owner,
                parseVisibility(visibility), parseUserIds(userIds));
        return ReturnResult.ok(toMap(share, owner));
    }

    /**
     * 列出某台主机对当前用户可见的共享终端。
     *
     * @param hostId 主机标识
     * @param userId 当前用户标识
     * @return 共享记录列表（新→旧）
     */
    @Operation(summary = "共享终端列表")
    @GetMapping("/list")
    public ReturnResult<List<Map<String, Object>>> list(
            @RequestParam("hostId") Long hostId,
            @RequestParam(value = "userId", required = false, defaultValue = "anonymous") String userId) {
        String uid = blankTo(userId, "anonymous");
        return ReturnResult.ok(shareStore.listByHost(hostId, uid).stream()
                .map(s -> toMap(s, uid)).toList());
    }

    /**
     * 按共享标识取详情（含当前账号的权限位）。
     *
     * @param shareId 共享标识
     * @param userId  当前用户标识
     * @return 共享记录
     */
    @Operation(summary = "共享终端详情")
    @GetMapping("/{shareId}")
    public ReturnResult<Map<String, Object>> detail(
            @PathVariable("shareId") String shareId,
            @RequestParam(value = "userId", required = false, defaultValue = "anonymous") String userId) {
        TerminalShareStore.TerminalShare share = shareStore.get(shareId);
        if (share == null) {
            return ReturnResult.illegal("共享终端不存在或已取消");
        }
        return ReturnResult.ok(toMap(share, blankTo(userId, "anonymous")));
    }

    /**
     * 按房间号取共享（被邀请方加入前先校验权限）。
     *
     * @param roomId 终端房间号
     * @param userId 当前用户标识
     * @return 共享记录（含 canWrite / canRead）
     */
    @Operation(summary = "按房间号取共享（校验加入权限）")
    @GetMapping("/room/{roomId}")
    public ReturnResult<Map<String, Object>> byRoom(
            @PathVariable("roomId") String roomId,
            @RequestParam(value = "userId", required = false, defaultValue = "anonymous") String userId) {
        TerminalShareStore.TerminalShare share = shareStore.byRoom(roomId);
        if (share == null) {
            return ReturnResult.illegal("共享终端不存在或已取消");
        }
        return ReturnResult.ok(toMap(share, blankTo(userId, "anonymous")));
    }

    /**
     * 更新共享可见性与授权用户（仅发起者可改）。
     *
     * @param shareId    共享标识
     * @param userId     操作者用户标识
     * @param visibility 可见性：public / private
     * @param userIds    私有时的授权用户（逗号分隔）
     * @return 更新后的共享记录
     */
    @Operation(summary = "更新终端共享权限")
    @PutMapping("/{shareId}/access")
    public ReturnResult<Map<String, Object>> updateAccess(
            @PathVariable("shareId") String shareId,
            @RequestParam(value = "userId", required = false, defaultValue = "anonymous") String userId,
            @RequestParam(value = "visibility", required = false, defaultValue = "public") String visibility,
            @RequestParam(value = "userIds", required = false, defaultValue = "") String userIds) {
        String uid = blankTo(userId, "anonymous");
        TerminalShareStore.TerminalShare exist = shareStore.get(shareId);
        if (exist == null) {
            return ReturnResult.illegal("共享终端不存在或已取消");
        }
        if (!exist.owner().equals(uid)) {
            return ReturnResult.illegal("只有发起者可以修改共享权限");
        }
        TerminalShareStore.TerminalShare updated = shareStore.updateAccess(shareId,
                parseVisibility(visibility), parseUserIds(userIds));
        return ReturnResult.ok(toMap(updated, uid));
    }

    /**
     * 取消共享（仅发起者可取消）。
     *
     * @param shareId 共享标识
     * @param userId  操作者用户标识
     * @return 是否取消成功
     */
    @Operation(summary = "取消终端共享")
    @DeleteMapping("/{shareId}")
    public ReturnResult<Boolean> delete(
            @PathVariable("shareId") String shareId,
            @RequestParam(value = "userId", required = false, defaultValue = "anonymous") String userId) {
        String uid = blankTo(userId, "anonymous");
        TerminalShareStore.TerminalShare exist = shareStore.get(shareId);
        if (exist == null) {
            return ReturnResult.ok(false);
        }
        if (!exist.owner().equals(uid)) {
            return ReturnResult.illegal("只有发起者可以取消共享");
        }
        return ReturnResult.ok(shareStore.delete(shareId));
    }

    /**
     * 共享记录转 Map（前端统一结构，字段口径同 FileCollabController#toMap）。
     *
     * @param share   共享记录
     * @param userId  当前用户标识（用于计算权限位）
     * @return Map 快照
     */
    private static Map<String, Object> toMap(TerminalShareStore.TerminalShare share, String userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shareId", share.shareId());
        m.put("roomId", share.roomId());
        m.put("hostId", share.hostId());
        m.put("hostName", share.hostName());
        m.put("owner", share.owner());
        m.put("visibility", share.visibility().name());
        m.put("allowedUserIds", share.allowedUserIds());
        m.put("createdAt", share.createdAt());
        m.put("updatedAt", share.updatedAt());
        boolean canWrite = TerminalShareStore.canWrite(share, userId);
        m.put("canWrite", canWrite);
        m.put("isOwner", share.owner().equals(userId));
        return m;
    }

    /**
     * 解析可见性。
     *
     * @param visibility 原始值
     * @return 可见性枚举
     */
    private static TerminalShareStore.Visibility parseVisibility(String visibility) {
        return "private".equalsIgnoreCase(visibility)
                ? TerminalShareStore.Visibility.PRIVATE : TerminalShareStore.Visibility.PUBLIC;
    }

    /**
     * 解析逗号分隔的用户标识集合。
     *
     * @param userIds 原始字符串（可为空）
     * @return 去空去重集合
     */
    private static Set<String> parseUserIds(String userIds) {
        Set<String> result = new LinkedHashSet<>();
        if (userIds == null || userIds.isBlank()) {
            return result;
        }
        for (String item : userIds.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 空值兜底。
     *
     * @param value    原始值
     * @param fallback 兜底值
     * @return 非空值
     */
    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
