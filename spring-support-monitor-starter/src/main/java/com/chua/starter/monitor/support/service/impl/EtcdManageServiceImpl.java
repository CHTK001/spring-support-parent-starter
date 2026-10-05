package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.monitor.support.middleware.EtcdClient;
import com.chua.starter.monitor.support.middleware.MiddlewareEndpoint;
import com.chua.starter.monitor.support.model.EtcdKeyDetailVO;
import com.chua.starter.monitor.support.model.EtcdKeyVO;
import com.chua.starter.monitor.support.model.EtcdLeaseVO;
import com.chua.starter.monitor.support.model.EtcdMemberVO;
import com.chua.starter.monitor.support.model.EtcdStatusVO;
import com.chua.starter.monitor.support.model.EtcdTestVO;
import com.chua.starter.monitor.support.model.dto.EtcdKeyDTO;
import com.chua.starter.monitor.support.model.dto.EtcdKeyQueryDTO;
import com.chua.starter.monitor.support.model.dto.EtcdKeySaveDTO;
import com.chua.starter.monitor.support.model.dto.EtcdLeaseDTO;
import com.chua.starter.monitor.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.monitor.support.service.EtcdManageService;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * etcd 管理服务实现。
 *
 * <p>只做「取数 + 归一」，不在这里拼 SQL/命令；远端错误统一由控制器翻成业务错误信封。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtcdManageServiceImpl implements EtcdManageService {

    /**
     * 值的可读预览上限，超过时只提示长度（避免把大 value 整个塞进列表接口）
     */
    private static final int VALUE_PREVIEW_LIMIT = 1024;

    /**
     * etcd 客户端
     */
    private final EtcdClient client;

    @Override
    public EtcdTestVO test(MiddlewareTestDTO request) {
        EtcdClient.Target target = testTarget(request);
        try {
            // 用 probe（状态 + 强制鉴权一次），不要只调 status：status 在 etcd 上免鉴权，
            // 单用它会让错密码也报"连接成功"。
            EtcdClient.Server server = client.probe(target);
            return new EtcdTestVO(true, emptyToNull(server.version()), emptyToNull(server.leader()), "连接成功");
        } catch (IllegalStateException e) {
            return new EtcdTestVO(false, null, null, e.getMessage());
        }
    }

    @Override
    public EtcdStatusVO status(Long datasourceId) {
        EtcdClient.Server server = client.status(client.target(datasourceId));
        return new EtcdStatusVO(emptyToNull(server.version()), emptyToNull(server.leader()), server.dbSize(),
                server.dbSizeInUse(), server.raftIndex(), server.raftTerm(), server.revision());
    }

    @Override
    public List<EtcdKeyVO> keys(EtcdKeyQueryDTO query) {
        EtcdClient.Target target = client.target(query == null ? null : query.datasourceId());
        String prefix = query == null ? "" : trim(query.prefix());
        int limit = query == null || query.limit() == null ? 0 : query.limit();
        boolean keysOnly = query == null || query.keysOnly() == null || query.keysOnly();
        JsonObject json = client.range(target, prefix, limit, keysOnly);
        JsonArray kvs = json.getJsonArray("kvs");
        List<EtcdKeyVO> result = new ArrayList<>();
        if (kvs == null) {
            return result;
        }
        for (int i = 0; i < kvs.size(); i++) {
            JsonObject kv = kvs.getJsonObject(i);
            if (kv == null) {
                continue;
            }
            result.add(new EtcdKeyVO(textOf(kv.get("key")), longOrNull(kv.get("create_revision")),
                    longOrNull(kv.get("mod_revision")), longOrNull(kv.get("version")), longOrNull(kv.get("lease"))));
        }
        return result;
    }

    @Override
    public EtcdKeyDetailVO key(String key, EtcdKeyDTO dto) {
        String name = requireText(key, "键名不能为空");
        EtcdClient.Target target = client.target(dto == null ? null : dto.datasourceId());
        JsonObject json = client.getKey(target, name);
        JsonArray kvs = json.getJsonArray("kvs");
        if (kvs == null || kvs.isEmpty()) {
            throw new IllegalStateException("键不存在: " + name);
        }
        JsonObject kv = kvs.getJsonObject(0);
        byte[] raw = EtcdClient.decode(str(kv.get("value")));
        String decoded = decodeUtf8(raw);
        boolean binary = decoded == null;
        Long lease = longOrNull(kv.get("lease"));
        Long ttl = null;
        if (lease != null && lease > 0L) {
            ttl = leaseTtl(target, lease);
        }
        return new EtcdKeyDetailVO(name,
                binary ? "（二进制内容，" + raw.length + " 字节，已省略）" : abbreviateValue(decoded, raw.length),
                binary, raw.length, longOrNull(kv.get("create_revision")), longOrNull(kv.get("mod_revision")),
                longOrNull(kv.get("version")), lease == null ? 0L : lease, ttl);
    }

    @Override
    public boolean save(EtcdKeySaveDTO request) {
        String key = requireText(request == null ? null : request.key(), "键名不能为空");
        EtcdClient.Target target = client.target(request == null ? null : request.datasourceId());
        long lease = request == null || request.lease() == null ? 0L : request.lease();
        client.put(target, key, request == null ? "" : request.value(), lease);
        return true;
    }

    @Override
    public long delete(String key, EtcdKeyDTO dto) {
        String name = requireText(key, "键名不能为空");
        EtcdClient.Target target = client.target(dto == null ? null : dto.datasourceId());
        boolean prefix = dto != null && Boolean.TRUE.equals(dto.prefix());
        return client.delete(target, name, prefix);
    }

    @Override
    public List<EtcdLeaseVO> leases(Long datasourceId) {
        EtcdClient.Target target = client.target(datasourceId);
        List<JsonObject> raw = client.leases(target);
        List<EtcdLeaseVO> result = new ArrayList<>(raw.size());
        for (JsonObject item : raw) {
            Long id = longOrNull(item.get("ID"));
            if (id == null) {
                continue;
            }
            // 实测（3.5.17）：/v3/lease/leases 只回 {"ID":"..."}，**没有 TTL / grantedTTL**，
            // 两个时间都只能从 timetolive 拿；键数量也只能从那里拿（带 keys:true）。
            Long ttl = longOrNull(item.get("TTL"));
            Long grantedTtl = longOrNull(item.get("grantedTTL"));
            Integer keyCount = null;
            try {
                JsonObject detail = client.timeToLive(target, id);
                if (ttl == null) {
                    ttl = longOrNull(detail.get("TTL"));
                }
                if (grantedTtl == null) {
                    grantedTtl = longOrNull(detail.get("grantedTTL"));
                }
                JsonArray keys = detail.getJsonArray("keys");
                keyCount = keys == null ? 0 : keys.size();
            } catch (IllegalStateException e) {
                // 租约可能在列举与查询之间过期，属正常竞态：计数留空而不是把整个列表打失败
                log.debug("[etcd] 读取租约 {} 的 TTL 失败: {}", id, e.getMessage());
            }
            result.add(new EtcdLeaseVO(id, ttl, grantedTtl, keyCount));
        }
        return result;
    }

    @Override
    public EtcdLeaseVO grantLease(EtcdLeaseDTO request) {
        Long ttl = request == null ? null : request.ttl();
        if (ttl == null || ttl <= 0L) {
            throw new IllegalArgumentException("TTL 必须为正整数秒");
        }
        EtcdClient.Target target = client.target(request.datasourceId());
        long id = client.grantLease(target, ttl);
        return new EtcdLeaseVO(id, ttl, ttl, 0);
    }

    @Override
    public boolean revokeLease(EtcdLeaseDTO request) {
        Long id = request == null ? null : request.leaseId();
        if (id == null || id <= 0L) {
            throw new IllegalArgumentException("租约 ID 不能为空");
        }
        client.revokeLease(client.target(request.datasourceId()), id);
        return true;
    }

    @Override
    public List<EtcdMemberVO> members(Long datasourceId) {
        EtcdClient.Target base = client.target(datasourceId);
        String leaderId = "";
        try {
            leaderId = trim(client.status(base).leader());
        } catch (IllegalStateException e) {
            log.debug("[etcd] 读取 leader 失败: {}", e.getMessage());
        }
        List<JsonObject> raw = client.members(base);
        List<EtcdMemberVO> result = new ArrayList<>(raw.size());
        for (JsonObject member : raw) {
            // ID / name 是**明文 JSON 字符串**，不是 base64（key/value 才是 base64）。
            // 早先对它们套了 base64 解码，成员名直接变成乱码（实测 'default' → 'u�ں['）。
            String id = trim(str(member.get("ID")));
            String name = trim(str(member.get("name")));
            String peers = joinUrls(member.getJsonArray("peerURLs"));
            String clients = joinUrls(member.getJsonArray("clientURLs"));
            // 逐个成员取状态：集群里挂一个成员是常见故障，页面要能看出是哪一个，
            // 而不是把整个列表打失败。
            String version = "";
            Long dbSize = null;
            boolean healthy = false;
            String error = "";
            String probe = firstUrl(member.getJsonArray("clientURLs"));
            if (probe.isEmpty()) {
                error = "该成员未上报 clientURL，无法探测";
            } else {
                try {
                    // 同 test：必须走 probe（status 免鉴权，单用会把"凭据没带上"误报成"成员健康"）
                    EtcdClient.Server server = client.probe(memberTarget(base, probe));
                    version = trim(server.version());
                    dbSize = server.dbSize();
                    healthy = true;
                } catch (IllegalStateException e) {
                    error = e.getMessage();
                }
            }
            result.add(new EtcdMemberVO(id, name, peers, clients, !leaderId.isEmpty() && leaderId.equals(id),
                    healthy, emptyToNull(version), dbSize, emptyToNull(error)));
        }
        return result;
    }

    /**
     * 解析测试目标：优先库中数据源，其次即席填写的地址，最后落到第一个 etcd 数据源。
     *
     * @param request 测试请求，可为空
     * @return 访问目标
     */
    private EtcdClient.Target testTarget(MiddlewareTestDTO request) {
        if (request == null || request.host() == null || request.host().isBlank()) {
            return client.target(request == null ? null : request.datasourceId());
        }
        if (request.datasourceId() != null && request.datasourceId() > 0L) {
            return client.target(request.datasourceId());
        }
        DataSourceVO adhoc = new DataSourceVO(null, "ad-hoc", EtcdClient.DS_TYPE, request.host().trim(),
                request.port(), request.contextPath(), request.username(), request.password(), null, 1, null, null, null,
                null, null, null, null, null, null, null);
        return client.target(adhoc);
    }

    /**
     * 构造指向某个集群成员的探测目标（**沿用数据源的鉴权凭据**）。
     *
     * <p>etcd 开启鉴权时，读某个成员的状态同样要带 token；不继承凭据会让成员一律显示"不健康"，
     * 把"鉴权没带上"误报成"节点挂了"。</p>
     *
     * @param base     原目标
     * @param clientUrl 成员的 clientURL
     * @return 成员目标
     */
    private static EtcdClient.Target memberTarget(EtcdClient.Target base, String clientUrl) {
        URI uri = URI.create(clientUrl.trim());
        String authority = uri.getAuthority() == null ? uri.toString() : uri.getAuthority();
        MiddlewareEndpoint endpoint = new MiddlewareEndpoint(uri.getScheme() + "://" + authority, "",
                base.endpoint().username(), base.endpoint().password());
        return new EtcdClient.Target(endpoint, base.username(), base.password());
    }

    /**
     * 读取租约剩余 TTL。
     *
     * @param target  访问目标
     * @param leaseId 租约 ID
     * @return 剩余秒数，读不到为 null
     */
    private Long leaseTtl(EtcdClient.Target target, long leaseId) {
        try {
            return longOrNull(client.timeToLive(target, leaseId).get("TTL"));
        } catch (IllegalStateException e) {
            log.debug("[etcd] 读取键的租约 TTL 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 值的展示文本：超长时截断并提示总长度。
     *
     * @param value  文本值
     * @param length 原始字节长度
     * @return 展示文本
     */
    private static String abbreviateValue(String value, int length) {
        String text = value == null ? "" : value;
        if (text.length() <= VALUE_PREVIEW_LIMIT) {
            return text;
        }
        return text.substring(0, VALUE_PREVIEW_LIMIT) + "\n…（已截断，总长 " + length + " 字节）";
    }

    /**
     * 严格按 UTF-8 解码；含非法字节序列时返回 null（视为二进制）。
     *
     * <p>用严格模式：宽松模式会把二进制内容解成一串 U+FFFD 塞进页面，
     * 看起来像"乱码的值"而不是"这是二进制"。</p>
     *
     * @param bytes 字节
     * @return 文本，非 UTF-8 时为 null
     */
    private static String decodeUtf8(byte[] bytes) {
        if (bytes.length == 0) {
            return "";
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * 取 kv 里的键名字节并解码成文本。
     *
     * @param value base64 值
     * @return 键名
     */
    private static String textOf(Object value) {
        return new String(EtcdClient.decode(value == null ? "" : String.valueOf(value)), StandardCharsets.UTF_8);
    }

    /**
     * 把 URL 数组拼成逗号分隔文本。
     *
     * @param array 数组，可为空
     * @return 文本
     */
    private static String joinUrls(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.valueOf(array.get(i)));
        }
        return builder.toString();
    }

    /**
     * 取 URL 数组的第一个。
     *
     * @param array 数组，可为空
     * @return 第一个 URL，空数组返回空串
     */
    private static String firstUrl(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return "";
        }
        return String.valueOf(array.get(0)).trim();
    }

    /**
     * 校验必填文本。
     *
     * @param value 原值
     * @param message 错误文案
     * @return 去空白后的文本
     */
    private static String requireText(String value, String message) {
        String text = trim(value);
        if (text.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    /**
     * 解析 etcd 以字符串返回的数值。
     *
     * @param value 原值
     * @return 数值，空或不可解析为 null
     */
    private static Long longOrNull(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 对象转字符串。
     *
     * @param value 原值
     * @return 字符串，null 转空串
     */
    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 空串转 null（便于前端区分「没有」与「空值」）。
     *
     * @param value 原值
     * @return null 或原值
     */
    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * 空值安全的去空白。
     *
     * @param value 原值
     * @return 结果
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
