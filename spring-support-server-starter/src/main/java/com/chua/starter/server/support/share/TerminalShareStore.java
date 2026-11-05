// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.share;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 终端共享存储器（内存态）。
 *
 * <p>共享口径与文件管理协同（{@code /v2/file/collab}）<b>完全一致</b>：
 * 每条共享记录持有发起者（owner）、可见性（公开 / 私有）、被授权用户集合，
 * 以及一个终端房间号（{@code roomId}）。SSH 直连处理器按 {@code roomId} 把多个
 * 浏览器会话接到同一条 SSH 连接与同一个远端伪终端上，实现「邀请用户协同操作」。</p>
 *
 * <p>权限规则（与 {@code FileCollabStore} 同一条判定）：</p>
 * <ul>
 *   <li>公开 —— 全员可读可写；</li>
 *   <li>私有 —— 仅发起者与被授权用户可读可写；</li>
 *   <li>其余用户 —— 只读（前端按 {@code canWrite=false} 禁用输入）。</li>
 * </ul>
 *
 * <p>数据仅存内存：服务重启即清空。终端共享本就与在线会话同生命周期，
 * 重启后房间与连接都不复存在，无需持久化。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Component
public class TerminalShareStore {

    /**
     * 共享记录：共享标识 → 记录
     */
    private final Map<String, TerminalShare> shares = new ConcurrentHashMap<>();

    /**
     * 创建（或复用）一条终端共享记录。
     *
     * <p>同一台主机同一房间号只保留一条记录：发起者重复点「共享」时更新可见性
     * 与授权用户，而不是堆出多条指向同一房间的记录。</p>
     *
     * @param hostId         主机标识
     * @param hostName       主机名（展示用）
     * @param roomId         终端房间号（前端生成，跨账号加入凭它）
     * @param owner          发起者用户标识
     * @param visibility     可见性：PUBLIC / PRIVATE，空按公开
     * @param allowedUserIds 私有时的授权用户集合
     * @return 共享记录
     */
    public TerminalShare create(Long hostId, String hostName, String roomId, String owner,
                                Visibility visibility, Set<String> allowedUserIds) {
        String rid = roomId == null || roomId.isBlank() ? newRoomId() : roomId.trim();
        TerminalShare exist = byRoom(rid);
        long now = System.currentTimeMillis();
        if (exist != null) {
            TerminalShare updated = new TerminalShare(exist.shareId(), hostId, hostName, rid,
                    exist.owner(), visibility == null ? exist.visibility() : visibility,
                    copy(allowedUserIds), exist.createdAt(), now);
            shares.put(updated.shareId(), updated);
            return updated;
        }
        TerminalShare created = new TerminalShare(newShareId(), hostId, hostName, rid, owner,
                visibility == null ? Visibility.PUBLIC : visibility, copy(allowedUserIds), now, now);
        shares.put(created.shareId(), created);
        return created;
    }

    /**
     * 按共享标识取记录。
     *
     * @param shareId 共享标识
     * @return 记录或 null
     */
    public TerminalShare get(String shareId) {
        return shareId == null ? null : shares.get(shareId);
    }

    /**
     * 按房间号取记录。
     *
     * @param roomId 终端房间号
     * @return 记录或 null
     */
    public TerminalShare byRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return null;
        }
        for (TerminalShare s : shares.values()) {
            if (roomId.equals(s.roomId())) {
                return s;
            }
        }
        return null;
    }

    /**
     * 某台主机对指定用户可见的共享记录（新→旧）。
     *
     * @param hostId 主机标识
     * @param userId 当前用户标识（可为空，空按匿名处理）
     * @return 可见记录列表
     */
    public List<TerminalShare> listByHost(Long hostId, String userId) {
        String uid = userId == null ? "" : userId;
        List<TerminalShare> result = new ArrayList<>();
        for (TerminalShare s : shares.values()) {
            if (!s.hostId().equals(hostId)) {
                continue;
            }
            if (s.visibility() != Visibility.PUBLIC
                    && !s.owner().equals(uid) && !s.allowedUserIds().contains(uid)) {
                continue;
            }
            result.add(s);
        }
        result.sort(Comparator.comparingLong(TerminalShare::updatedAt).reversed());
        return Collections.unmodifiableList(result);
    }

    /**
     * 更新可见性与授权用户（仅发起者可调用，判定在控制器层）。
     *
     * @param shareId        共享标识
     * @param visibility     新可见性（null 表示不变）
     * @param allowedUserIds 新授权用户集合（null 表示不变）
     * @return 更新后的记录；不存在返回 null
     */
    public TerminalShare updateAccess(String shareId, Visibility visibility, Set<String> allowedUserIds) {
        TerminalShare s = shares.get(shareId);
        if (s == null) {
            return null;
        }
        TerminalShare updated = new TerminalShare(s.shareId(), s.hostId(), s.hostName(), s.roomId(),
                s.owner(), visibility == null ? s.visibility() : visibility,
                allowedUserIds == null ? s.allowedUserIds() : copy(allowedUserIds),
                s.createdAt(), System.currentTimeMillis());
        shares.put(shareId, updated);
        return updated;
    }

    /**
     * 取消共享。
     *
     * @param shareId 共享标识
     * @return 删除前是否存在
     */
    public boolean delete(String shareId) {
        return shareId != null && shares.remove(shareId) != null;
    }

    /**
     * 当前共享记录总数。
     *
     * @return 总数
     */
    public int size() {
        return shares.size();
    }

    /**
     * 判定某用户对一条记录是否有写权限（与文件协同同一条判定）。
     *
     * @param share  共享记录
     * @param userId 用户标识
     * @return true 表示可协同操作；false 表示只读
     */
    public static boolean canWrite(TerminalShare share, String userId) {
        if (share == null) {
            return false;
        }
        String uid = userId == null ? "" : userId;
        if (share.visibility() == Visibility.PUBLIC) {
            return true;
        }
        return share.owner().equals(uid) || share.allowedUserIds().contains(uid);
    }

    /**
     * 生成新房间号（前端未给定时兜底）。
     *
     * @return 房间号
     */
    private static String newRoomId() {
        return "room-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * 生成新共享标识。
     *
     * @return 共享标识
     */
    private static String newShareId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 复制授权集合（去空）。
     *
     * @param source 原始集合
     * @return 新集合，source 为 null 时返回空集合
     */
    private static Set<String> copy(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(source);
    }

    /**
     * 可见性：公开（全员可协同操作）/ 私有（仅发起者与被授权用户可协同操作）。
     */
    public enum Visibility {
        /**
         * 公开
         */
        PUBLIC,
        /**
         * 私有
         */
        PRIVATE
    }

    /**
     * 一条终端共享记录。
     *
     * @param shareId        共享标识
     * @param hostId         主机标识
     * @param hostName       主机名
     * @param roomId         终端房间号
     * @param owner          发起者用户标识
     * @param visibility     可见性
     * @param allowedUserIds 私有授权用户集合
     * @param createdAt      创建时间
     * @param updatedAt      更新时间
     */
    public record TerminalShare(String shareId, Long hostId, String hostName, String roomId,
                                String owner, Visibility visibility, Set<String> allowedUserIds,
                                long createdAt, long updatedAt) {
    }
}