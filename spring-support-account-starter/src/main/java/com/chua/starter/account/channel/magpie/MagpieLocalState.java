// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.starter.common.support.channel.MagpieSupport;
import com.chua.starter.common.support.text.json.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Magpie 本地状态文件的只读视图：管理面（{@code :3430/api/*}）不可达时的同机回落。
 *
 * <p><b>只适用于同机部署</b>：桌面版 magpie 只监听数据面 {@code 127.0.0.1:3425}，
 * 不提供 {@code :3430} 管理面，但它把状态写在 {@code <配置目录>} 下的三个文件里：</p>
 * <ul>
 *   <li>{@code providers.json}：{@code {providers:[...], groups:[...], ...}}；</li>
 *   <li>{@code quotas.json}：{@code {"<provider>/<account>": {at, quota:{...}, spans:[...]}}}；</li>
 *   <li>{@code plugins.json}：{@code {plugins:[{spec:"@scope/name"}, ...]}}。</li>
 * </ul>
 *
 * <p>本类<b>只读</b>这三个固定文件（不做「读任意路径」的通用接口），返回的数据形状与
 * {@link MagpieAdminClient} 一致，好让上层代码不必区分数据来自 HTTP 还是磁盘；
 * 不写回、不改 magpie 的状态。远程网关不适用：磁盘上的文件与远端进程无关。</p>
 *
 * <p><b>失败一律返回 {@code null}</b>：目录 / 文件不存在、解析失败（多半是 magpie
 * 正在写盘，隔 50ms 重试一次）、字段缺失，都不向上抛异常，由调用方决定是继续抛原异常
 * 还是降级为「本地读不到」。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
public final class MagpieLocalState {

    /**
     * provider 列表文件名。
     */
    private static final String FILE_PROVIDERS = "providers.json";

    /**
     * 额度卡文件名。
     */
    private static final String FILE_QUOTAS = "quotas.json";

    /**
     * 插件清单文件名。
     */
    private static final String FILE_PLUGINS = "plugins.json";

    /**
     * {@code providers.json} 里 provider 数组的字段名。
     */
    private static final String FIELD_PROVIDERS = "providers";

    /**
     * {@code quotas.json} 里每张卡内嵌的 {@code quota} 子对象字段名。
     */
    private static final String FIELD_QUOTA = "quota";

    /**
     * {@code plugins.json} 里插件数组的字段名。
     */
    private static final String FIELD_PLUGINS = "plugins";

    /**
     * 插件条目的包名 / 规格字段（为空的行直接丢弃）。
     */
    private static final String FIELD_SPEC = "spec";

    /**
     * 返回结构里的 {@code state} 键。
     */
    private static final String FIELD_STATE = "state";

    /**
     * 本地没有插件市场数据时给前端的标记字段。
     */
    private static final String FIELD_LOCAL_ONLY = "localOnly";

    /**
     * 额度卡里的 provider 字段名（过滤用）。
     */
    private static final String FIELD_PROVIDER = "provider";

    /**
     * 额度卡里的账号字段名（过滤用）。
     */
    private static final String FIELD_USER = "user";

    /**
     * 文件读取次数（含首次）：magpie 运行期反复改写，首次失败隔一会儿再读一次。
     */
    private static final int READ_ATTEMPTS = 2;

    /**
     * 重读前的等待（毫秒）。
     */
    private static final long RETRY_MILLIS = 50L;

    /**
     * 工具类，禁止实例化。
     */
    private MagpieLocalState() {
    }

    /**
     * 读 {@code providers.json} 的上游列表。
     *
     * <p>元素原样返回，顺序保持文件里的顺序（与 {@code GET /api/providers} 的
     * {@code providers} 数组同形），不做脱敏、不补字段。</p>
     *
     * @param credentials 连接凭据（其中可指定 magpie 配置目录）
     * @return provider 列表；目录 / 文件不存在或解析失败返回 {@code null}
     */
    public static List<Map<String, Object>> providers(Map<String, Object> credentials) {
        Map<String, Object> root = readObject(credentials, FILE_PROVIDERS);
        if (root == null) {
            return null;
        }
        return asMapList(root.get(FIELD_PROVIDERS));
    }

    /**
     * 读 {@code quotas.json} 的额度卡，并按 provider / 账号过滤。
     *
     * <p>文件里每张卡形如 {@code "<provider>/<account>": {quota:{provider,user,windows,...}}},
     * 这里只取其中的 {@code quota} 子对象（与 {@code GET /api/usage/quotas} 的数组元素同形）。
     * 过滤规则与 {@link MagpieAdminClient#adminQuotas(Map, String, String)} 完全一致：
     * {@code provider} 为空表示不过滤（此时也不按 {@code user} 过滤），否则两者都精确等于。</p>
     *
     * @param credentials 连接凭据（其中可指定 magpie 配置目录）
     * @param provider    只取该 provider（可空）
     * @param user        只取该账号（可空）
     * @return 配额行列表；目录 / 文件不存在或解析失败返回 {@code null}
     */
    public static List<Map<String, Object>> quotas(Map<String, Object> credentials,
                                                   String provider, String user) {
        Map<String, Object> root = readObject(credentials, FILE_QUOTAS);
        if (root == null) {
            return null;
        }
        List<Map<String, Object>> rows = new ArrayList<>(root.size());
        for (Object value : root.values()) {
            if (!(value instanceof Map<?, ?> entry)) {
                continue;
            }
            Object quota = entry.get(FIELD_QUOTA);
            if (quota instanceof Map<?, ?> map) {
                rows.add(toStringKeyMap(map));
            }
        }
        return filterQuotas(rows, provider, user);
    }

    /**
     * 读 {@code plugins.json} 的插件清单。
     *
     * <p>只保留 {@code spec} 非空的条目（magpie 落盘时可能带占位行），返回
     * {@code {state:{plugins:[...]}}}, 与 {@code GET /api/plugins} 的外层结构一致。
     * 本地没有市场数据：{@code market=true} 时也只回 {@code state}，并标注
     * {@code localOnly=true}，供前端提示「这是本地读数」。</p>
     *
     * @param credentials 连接凭据（其中可指定 magpie 配置目录）
     * @param market      是否请求了插件市场（本地没有市场数据，仅在 state 里标注）
     * @return {@code {state:{plugins:[...]}}}；目录 / 文件不存在或解析失败返回 {@code null}
     */
    public static Map<String, Object> plugins(Map<String, Object> credentials, boolean market) {
        Map<String, Object> root = readObject(credentials, FILE_PLUGINS);
        if (root == null) {
            return null;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        if (root.get(FIELD_PLUGINS) instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> row = toStringKeyMap(map);
                Object spec = row.get(FIELD_SPEC);
                if (!(spec instanceof String text) || text.isBlank()) {
                    continue;
                }
                row.put(FIELD_SPEC, text.trim());
                rows.add(row);
            }
        }
        Map<String, Object> state = new LinkedHashMap<>(4);
        state.put(FIELD_PLUGINS, rows);
        if (market) {
            state.put(FIELD_LOCAL_ONLY, true);
        }
        Map<String, Object> out = new LinkedHashMap<>(2);
        out.put(FIELD_STATE, state);
        return out;
    }

    /**
     * 按 provider / 账号过滤额度行（语义照抄 {@link MagpieAdminClient} 的同名规则）。
     *
     * @param rows     原始行
     * @param provider provider id（可空）
     * @param user     账号（可空）
     * @return 过滤后的行
     */
    private static List<Map<String, Object>> filterQuotas(List<Map<String, Object>> rows,
                                                          String provider, String user) {
        if (MagpieSupport.isBlank(provider)) {
            return rows;
        }
        String wantedProvider = provider.trim();
        String wantedUser = MagpieSupport.isBlank(user) ? null : user.trim();
        List<Map<String, Object>> filtered = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String rowProvider = String.valueOf(row.get(FIELD_PROVIDER) == null ? "" : row.get(FIELD_PROVIDER));
            if (!wantedProvider.equalsIgnoreCase(rowProvider)) {
                continue;
            }
            if (wantedUser != null) {
                String rowUser = String.valueOf(row.get(FIELD_USER) == null ? "" : row.get(FIELD_USER));
                if (!wantedUser.equalsIgnoreCase(rowUser)) {
                    continue;
                }
            }
            filtered.add(row);
        }
        return filtered;
    }

    /**
     * 读配置目录下的一个固定 JSON 文件并解析成对象。
     *
     * <p>文件不存在直接返回 {@code null}；解析失败（多半是 magpie 正在写盘）隔
     * {@link #RETRY_MILLIS} 毫秒重读一次，仍失败返回 {@code null}，不抛异常。</p>
     *
     * @param credentials 连接凭据（其中可指定 magpie 配置目录）
     * @param fileName    配置目录下的固定文件名
     * @return 解析结果；不可用时返回 {@code null}
     */
    private static Map<String, Object> readObject(Map<String, Object> credentials, String fileName) {
        String dir = MagpieSupport.configDir(credentials);
        if (MagpieSupport.isBlank(dir)) {
            return null;
        }
        Path file = Paths.get(dir, fileName);
        for (int attempt = 0; attempt < READ_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                sleepQuietly();
            }
            if (!Files.isRegularFile(file)) {
                return null;
            }
            Map<String, Object> parsed = parseFile(file);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    /**
     * 解析一个 JSON 文件（对象形态）。
     *
     * @param file 文件
     * @return 字符串键 Map；读取或解析失败返回 {@code null}
     */
    private static Map<String, Object> parseFile(Path file) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
            Object parsed = Json.fromJson(text, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return toStringKeyMap(map);
            }
        } catch (Exception e) {
            // 文件正被 magpie 改写：交给调用方重试一次，两次都失败就当作读不到
        }
        return null;
    }

    /**
     * 归一化「对象列表」（兼容 List / 单对象 / null，语义与 {@link MagpieAdminClient} 一致）。
     *
     * @param raw 原始节点
     * @return 对象列表；字段缺失时返回空列表
     */
    private static List<Map<String, Object>> asMapList(Object raw) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add(toStringKeyMap(map));
                }
            }
        } else if (raw instanceof Map<?, ?> map) {
            rows.add(toStringKeyMap(map));
        }
        return rows;
    }

    /**
     * 拷贝成 {@code Map<String,Object>}。
     *
     * @param map 原始 Map
     * @return 字符串键副本
     */
    private static Map<String, Object> toStringKeyMap(Map<?, ?> map) {
        Map<String, Object> row = new LinkedHashMap<>(map.size());
        map.forEach((key, value) -> {
            if (key != null) {
                row.put(String.valueOf(key), value);
            }
        });
        return row;
    }

    /**
     * 静默等待一小段时间（解析重试用），被中断时恢复中断标记。
     */
    private static void sleepQuietly() {
        try {
            Thread.sleep(RETRY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
