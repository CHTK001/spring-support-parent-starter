package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerConfig;
import com.chua.starter.server.support.service.ServerConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 字符终端配色方案接口。
 *
 * <p>终端在 iframe 里跑（{@code /ssh-direct.html}、{@code /telnet-direct.html}），
 * 配色写死在页面里就无法统一调整。这里把「一套配色」当成平台级配置存起来：
 * 后端持久化到 {@code ms_server_config}（键 {@value #CONFIG_KEY}，值为 JSON 对象），
 * 外层页面取到后通过 iframe 参数与 postMessage 下发给终端。</p>
 *
 * <p><b>为什么是平台级而不是按人</b>：server 模块当前没有「当前登录人」的取值入口
 * （{@code ServerConfigService} 与终端 WS 处理器都不依赖账号上下文），
 * 配色的作用对象又是一块共享的操作台。等以后有了账号上下文，把配置键改成
 * {@code terminal.theme.<userId>} 即可，前端接口契约不用动。</p>
 *
 * <p>只接受 21 个 xterm 调色板键，值只接受 {@code #rgb} / {@code #rrggbb} /
 * {@code #rrggbbaa}：前端用取色器编辑，其它写法（{@code rgba(...)}、颜色名）
 * 会让「十六进制输入框」与取色器各说一套，干脆在校验处一次性收口。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Slf4j
@Tag(name = "服务器资产-终端配色", description = "字符终端的配色方案（持久化到系统配置）")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/terminal/theme")
@RequiredArgsConstructor
public class TerminalThemeController {

    /**
     * 配色在系统配置里的键
     */
    public static final String CONFIG_KEY = "terminal.theme";

    /**
     * JSON 解析器（读库里的配色对象）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 允许编辑的调色板键，顺序即前端设置面板的显示顺序。
     *
     * <p>前五个是界面色（底/字/光标/光标字/选区），后十六个是 ANSI 标准色与明亮色。</p>
     */
    private static final List<String> COLOR_KEYS = List.of(
            "background", "foreground", "cursor", "cursorAccent", "selectionBackground",
            "black", "red", "green", "yellow", "blue", "magenta", "cyan", "white",
            "brightBlack", "brightRed", "brightGreen", "brightYellow",
            "brightBlue", "brightMagenta", "brightCyan", "brightWhite");

    /**
     * 颜色格式：十六进制，3/6/8 位（8 位带透明度）
     */
    private static final Pattern HEX_COLOR = Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    /**
     * 自定义预设的配置键前缀：一套预设一行（{@code terminal.theme.preset.<id>}）。
     *
     * <p>不把预设塞进一条 JSON 数组：{@code ms_server_config_value} 只有
     * {@code VARCHAR(2048)}，而一套配色就占约 600 字符，两三套就会溢出（写库报
     * data too long 或静默截断）。一行一套既不会撑爆，删除也只是一次按 key 的 delete。</p>
     */
    private static final String PRESET_PREFIX = "terminal.theme.preset.";

    /**
     * 自定义预设上限（每行约 600 字符，20 套约 12KB，与配色本身互不影响）
     */
    private static final int MAX_PRESETS = 20;

    /**
     * 预设名称最大长度
     */
    private static final int MAX_PRESET_NAME = 40;

    /**
     * 预设标识白名单：id 会拼进配置键，必须限定字符集
     */
    private static final Pattern PRESET_ID = Pattern.compile("^[A-Za-z0-9]{1,40}$");

    /**
     * 默认配色。
     *
     * <p>底色与前景刻意沿用终端页原来的 {@code #111111/#e6e6e6}：升级后未调整过配色的
     * 用户看到的画面与之前一致；ANSI 十六色取自 VS Code 深色主题，保证常见
     * {@code ls --color}、git、vim 的输出对比度足够。</p>
     */
    private static final Map<String, String> DEFAULT_THEME;

    static {
        // [P3C A20 豁免]
        Map<String, String> defaults = new LinkedHashMap<>(32);
        defaults.put("background", "#111111");
        defaults.put("foreground", "#e6e6e6");
        defaults.put("cursor", "#e6e6e6");
        defaults.put("cursorAccent", "#111111");
        defaults.put("selectionBackground", "#264f78");
        defaults.put("black", "#000000");
        defaults.put("red", "#cd3131");
        defaults.put("green", "#0dbc79");
        defaults.put("yellow", "#e5e510");
        defaults.put("blue", "#2472c8");
        defaults.put("magenta", "#bc3fbc");
        defaults.put("cyan", "#11a8cd");
        defaults.put("white", "#e5e5e5");
        defaults.put("brightBlack", "#666666");
        defaults.put("brightRed", "#f14c4c");
        defaults.put("brightGreen", "#23d18b");
        defaults.put("brightYellow", "#f5f543");
        defaults.put("brightBlue", "#3b8eea");
        defaults.put("brightMagenta", "#d670d6");
        defaults.put("brightCyan", "#29b8db");
        defaults.put("brightWhite", "#ffffff");
        DEFAULT_THEME = Collections.unmodifiableMap(defaults);
    }

    /**
     * 系统配置服务
     */
    private final ServerConfigService configService;

    /**
     * 读取当前配色（缺省项用默认值补齐）。
     *
     * <p>永远返回完整的 21 个键：前端取色器与取色面板不必自己兜底，
     * 存量库里只存过一部分键时也不会出现空色块。</p>
     *
     * @return 完整配色
     */
    @Operation(summary = "读取终端配色")
    @GetMapping
    public ReturnResult<Map<String, String>> get() {
        return ReturnResult.ok(currentTheme());
    }

    /**
     * 保存配色（增量覆盖）。
     *
     * @param body 配色，可只传要改的键；未传的键保持<b>库里的当前值</b>
     * @return 保存后的完整配色
     */
    @Operation(summary = "保存终端配色")
    @PutMapping
    public ReturnResult<Map<String, String>> save(@RequestBody Map<String, String> body) {
        if (body == null || body.isEmpty()) {
            return ReturnResult.error("配色不能为空");
        }
        // 底稿是「库里的当前配色」，不是默认配色：以前以默认值为底，只传一个键的
        // 调用会把另外 20 个键静默改回默认（前端目前总发全量所以没暴露，但任何
        // 脚本/未来页面的部分更新都会踩），而且用户没法察觉自己刚调好的配色没了。
        Map<String, String> merged = new LinkedHashMap<>(currentTheme());
        for (Map.Entry<String, String> entry : body.entrySet()) {
            String key = entry.getKey();
            if (key == null || !COLOR_KEYS.contains(key)) {
                // 未知键直接忽略：前端多传一个键不该让整次保存失败，
                // 但也不能把它写进库（否则库里会出现永远没人渲染的垃圾字段）
                continue;
            }
            String value = entry.getValue() == null ? null : entry.getValue().trim();
            if (value == null || !HEX_COLOR.matcher(value).matches()) {
                return ReturnResult.error("颜色格式不合法: " + key + "=" + value + "（只接受 #rgb / #rrggbb / #rrggbbaa）");
            }
            merged.put(key, value.toLowerCase(Locale.ROOT));
        }
        configService.saveConfig(payloadOf(merged));
        log.info("[terminal] 终端配色已保存: {}", merged);
        return ReturnResult.ok(merged);
    }

    /**
     * 恢复默认配色。
     *
     * @return 默认配色
     */
    @Operation(summary = "恢复默认终端配色")
    @PostMapping("/reset")
    public ReturnResult<Map<String, String>> reset() {
        Map<String, String> defaults = new LinkedHashMap<>(DEFAULT_THEME);
        configService.saveConfig(payloadOf(defaults));
        return ReturnResult.ok(defaults);
    }

    /**
     * 列出自定义配色预设。
     *
     * @return 预设列表（按创建先后）
     */
    @Operation(summary = "列出自定义配色预设")
    @GetMapping("/presets")
    public ReturnResult<List<Preset>> presets() {
        return ReturnResult.ok(listPresets());
    }

    /**
     * 新增自定义配色预设。
     *
     * @param body 名称与配色（配色可只传要覆盖的键，其余用默认值补齐）
     * @return 新建的预设
     */
    @Operation(summary = "新增配色预设")
    @PostMapping("/presets")
    public ReturnResult<Preset> savePreset(@RequestBody PresetRequest body) {
        String rawName = body == null || body.name() == null ? "" : body.name().trim();
        if (rawName.isEmpty()) {
            return ReturnResult.error("预设名称不能为空");
        }
        // 一次赋值，便于后面在循环/日志里直接用（也避免 lambda 捕获非 final 变量）
        String name = rawName.length() > MAX_PRESET_NAME ? rawName.substring(0, MAX_PRESET_NAME) : rawName;
        Map<String, String> theme = normalize(body.theme());
        if (theme == null) {
            return ReturnResult.error("配色格式不合法（只接受 #rgb / #rrggbb / #rrggbbaa）");
        }
        List<Preset> existing = listPresets();
        if (existing.size() >= MAX_PRESETS) {
            return ReturnResult.error("自定义预设最多 " + MAX_PRESETS + " 套，请先删掉不用的");
        }
        // 同名预设会让面板里出现两个一模一样的名字，用户无从区分：宁可拒绝，
        // 也不要在列表里制造歧义（改名功能还没做，先让人换个名字或删旧的）
        for (Preset preset : existing) {
            if (preset.name().equals(name)) {
                return ReturnResult.error("已有同名预设「" + name + "」，换个名字或先删掉旧的");
            }
        }
        String id = nextPresetId(existing);
        Map<String, Object> stored = new LinkedHashMap<>(2);
        stored.put("name", name);
        stored.put("theme", theme);
        Map<String, Object> payload = new LinkedHashMap<>(2);
        payload.put(PRESET_PREFIX + id, stored);
        configService.saveConfig(payload);
        log.info("[terminal] 新增配色预设: {} ({})", name, id);
        return ReturnResult.ok(new Preset(id, name, theme));
    }

    /**
     * 更新预设：改名并用给定配色覆盖（id 不变）。
     *
     * <p>没有「只改名」的入口：这里的语义是「把这套预设更新成我现在调的样子」，
     * 改名只是顺带。名字唯一性校验要排除自己，否则「不改名直接覆盖」会被自己挡住。</p>
     *
     * @param id   预设标识
     * @param body 新名称与配色
     * @return 更新后的预设
     */
    @Operation(summary = "更新配色预设")
    @PutMapping("/presets/{id}")
    public ReturnResult<Preset> updatePreset(@PathVariable("id") String id,
                                             @RequestBody PresetRequest body) {
        if (id == null || !PRESET_ID.matcher(id).matches()) {
            return ReturnResult.error("预设标识不合法");
        }
        String rawName = body == null || body.name() == null ? "" : body.name().trim();
        if (rawName.isEmpty()) {
            return ReturnResult.error("预设名称不能为空");
        }
        String name = rawName.length() > MAX_PRESET_NAME ? rawName.substring(0, MAX_PRESET_NAME) : rawName;
        Map<String, String> theme = normalize(body.theme());
        if (theme == null) {
            return ReturnResult.error("配色格式不合法（只接受 #rgb / #rrggbb / #rrggbbaa）");
        }
        boolean exists = false;
        for (Preset preset : listPresets()) {
            if (preset.id().equals(id)) {
                exists = true;
                continue;
            }
            if (preset.name().equals(name)) {
                return ReturnResult.error("已有同名预设「" + name + "」，换个名字");
            }
        }
        if (!exists) {
            return ReturnResult.error("预设不存在，可能已被别的会话删除");
        }
        Map<String, Object> stored = new LinkedHashMap<>(2);
        stored.put("name", name);
        stored.put("theme", theme);
        Map<String, Object> payload = new LinkedHashMap<>(2);
        payload.put(PRESET_PREFIX + id, stored);
        configService.saveConfig(payload);
        log.info("[terminal] 更新配色预设: {} ({})", name, id);
        return ReturnResult.ok(new Preset(id, name, theme));
    }

    /**
     * 删除自定义配色预设。
     *
     * @param id 预设标识
     * @return 是否已删除
     */
    @Operation(summary = "删除配色预设")
    @DeleteMapping("/presets/{id}")
    public ReturnResult<Boolean> deletePreset(@PathVariable("id") String id) {
        if (id == null || !PRESET_ID.matcher(id).matches()) {
            return ReturnResult.error("预设标识不合法");
        }
        configService.deleteConfig(PRESET_PREFIX + id);
        log.info("[terminal] 删除配色预设: {}", id);
        return ReturnResult.ok(true);
    }

    /**
     * 组装 {@link ServerConfigService#saveConfig(Map)} 需要的入参。
     *
     * <p>不能直接写 {@code Map.of(CONFIG_KEY, theme)}：那个调的推断类型是
     * {@code Map<String, Map<String, String>>}，泛型不变性下赋不给
     * {@code Map<String, Object>}。</p>
     *
     * @param theme 配色
     * @return 配置键值
     */
    private Map<String, Object> payloadOf(Map<String, String> theme) {
        Map<String, Object> payload = new LinkedHashMap<>(2);
        payload.put(CONFIG_KEY, theme);
        return payload;
    }

    /**
     * 当前生效配色：默认值打底 + 库里存过的键覆盖。
     *
     * <p>库里只存了部分键（历史版本/手工改库）时，缺失项由默认值补齐；
     * 存了非法值则丢弃该键，避免把一段坏色值下发给终端。</p>
     *
     * <p>直接读 {@code ms_server_config} 的原始值再自己解析，而不是走
     * {@code getConfigMap()}：后者会把整库配置解析成一个扁平 Map（还会并上
     * guacd/ssh 等标量默认值），这里只关心一个键、值是 JSON 对象。</p>
     *
     * @return 完整配色
     */
    private Map<String, String> currentTheme() {
        Map<String, String> theme = new LinkedHashMap<>(DEFAULT_THEME);
        ReturnResult<List<MsServerConfig>> all = configService.listAll();
        List<MsServerConfig> rows = all == null ? null : all.getData();
        if (rows == null || rows.isEmpty()) {
            return theme;
        }
        for (MsServerConfig row : rows) {
            if (row == null || !CONFIG_KEY.equals(row.getConfigKey())) {
                continue;
            }
            Map<?, ?> storedMap = parseObject(row.getConfigValue());
            if (storedMap == null) {
                return theme;
            }
            for (String key : COLOR_KEYS) {
                Object value = storedMap.get(key);
                if (value == null) {
                    continue;
                }
                String text = String.valueOf(value).trim();
                if (HEX_COLOR.matcher(text).matches()) {
                    theme.put(key, text.toLowerCase(Locale.ROOT));
                }
            }
            return theme;
        }
        return theme;
    }

    /**
     * 把库里存的 JSON 对象解析成 Map。
     *
     * <p>解析失败（历史脏数据）返回 {@code null}，由调用方回退到默认配色 ——
     * 一段坏 JSON 不该让「打开终端」整条链路 500。</p>
     *
     * @param raw 原始存储值
     * @return 解析后的 Map，失败为 {@code null}
     */
    private Map<?, ?> parseObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            log.warn("[terminal] 终端配色配置解析失败，回退默认配色: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 读取全部自定义预设。
     *
     * <p>坏数据（手工改库改坏、键被塞了怪字符）直接跳过该行，不让一套预设把整个列表打挂。</p>
     *
     * @return 预设列表
     */
    private List<Preset> listPresets() {
        List<Preset> list = new ArrayList<>();
        ReturnResult<List<MsServerConfig>> all = configService.listAll();
        List<MsServerConfig> rows = all == null ? null : all.getData();
        if (rows == null || rows.isEmpty()) {
            return list;
        }
        for (MsServerConfig row : rows) {
            if (row == null || row.getConfigKey() == null
                    || !row.getConfigKey().startsWith(PRESET_PREFIX)) {
                continue;
            }
            Preset preset = parsePreset(row.getConfigKey(), row.getConfigValue());
            if (preset != null) {
                list.add(preset);
            }
        }
        return list;
    }

    /**
     * 把一行配置还原成预设。
     *
     * @param key 配置键
     * @param raw 存储值（{@code {"name":..., "theme":{...}}})
     * @return 预设；键或内容非法时返回 {@code null}
     */
    private Preset parsePreset(String key, String raw) {
        String id = key.substring(PRESET_PREFIX.length());
        if (!PRESET_ID.matcher(id).matches() || raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> stored = MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() { });
            Object name = stored.get("name");
            Map<String, String> theme = new LinkedHashMap<>(DEFAULT_THEME);
            Object themeRaw = stored.get("theme");
            if (themeRaw instanceof Map<?, ?> map) {
                for (String colorKey : COLOR_KEYS) {
                    Object value = map.get(colorKey);
                    if (value == null) {
                        continue;
                    }
                    String text = String.valueOf(value).trim();
                    if (HEX_COLOR.matcher(text).matches()) {
                        theme.put(colorKey, text.toLowerCase(Locale.ROOT));
                    }
                }
            }
            return new Preset(id, name == null ? "未命名" : String.valueOf(name), theme);
        } catch (Exception e) {
            log.warn("[terminal] 预设解析失败，跳过 {}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 生成一个未被占用的预设标识。
     *
     * <p>只用毫秒时间戳会撞：同一毫秒内的两次保存得到同一个 id，而
     * {@code saveConfig} 是按 key upsert —— 后一套会**静默覆盖**前一套。
     * 这里加四位随机盐，并且拿已加载的列表再核一次重。</p>
     *
     * @param existing 已存在的预设
     * @return 新标识
     */
    private static String nextPresetId(List<Preset> existing) {
        // 不用 lambda：candidate 在循环里重新赋值，捕获它会被编译器拒绝
        // [P3C A20 豁免]
        for (int attempt = 0; attempt < 64; attempt++) {
            String candidate = "p" + String.format(Locale.ROOT, "%d%04d",
                    System.currentTimeMillis(), ThreadLocalRandom.current().nextInt(10_000));
            boolean taken = false;
            for (Preset preset : existing) {
                if (preset.id().equals(candidate)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return candidate;
            }
        }
        // 极端情况下（64 次全撞）也不能返回空 id：退化成纳秒时间戳，仍满足字符白名单
        // （nanoTime 的原点任意、可能为负，取绝对值才不会被 PRESET_ID 判成非法）
        return "p" + Math.abs(System.nanoTime());
    }

    /**
     * 以默认配色打底校验一份配色：未传的键补默认，传了非法值则整份拒绝。
     *
     * <p>与 {@link #save(Map)} 的区别：保存允许忽略未知键、非法值整单拒绝；这里用于
     * 新建预设，语义一致，但独立出来便于返回 {@code null} 表示「校验不过」。</p>
     *
     * @param provided 预设里的配色
     * @return 完整配色；任一给定值非法时返回 {@code null}
     */
    private Map<String, String> normalize(Map<String, String> provided) {
        Map<String, String> theme = new LinkedHashMap<>(DEFAULT_THEME);
        if (provided == null || provided.isEmpty()) {
            return theme;
        }
        for (Map.Entry<String, String> entry : provided.entrySet()) {
            String colorKey = entry.getKey();
            if (colorKey == null || !COLOR_KEYS.contains(colorKey)) {
                continue;
            }
            String value = entry.getValue() == null ? null : entry.getValue().trim();
            if (value == null || !HEX_COLOR.matcher(value).matches()) {
                return null;
            }
            theme.put(colorKey, value.toLowerCase(Locale.ROOT));
        }
        return theme;
    }

    /**
     * 一套自定义配色预设。
     *
     * @param id    标识（配置键后缀）
     * @param name  名称
     * @param theme 完整配色（21 个键）
     */
    public record Preset(String id, String name, Map<String, String> theme) {
    }

    /**
     * 新增预设的请求体。
     *
     * @param name  名称
     * @param theme 配色，可只给要覆盖的键
     */
    public record PresetRequest(String name, Map<String, String> theme) {
    }
}
