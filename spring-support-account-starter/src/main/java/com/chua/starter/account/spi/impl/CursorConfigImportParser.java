package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.ChannelImportEntry;
import com.chua.starter.account.spi.ChannelImportFile;
import com.chua.starter.account.spi.ChannelImportModelDraft;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.chua.common.support.utils.StringUtils;

/**
 * Cursor state.vscdb 解析（键名启发式，best-effort）。
 *
 * <p>Cursor 没有公开的凭据导出格式，本解析器按 VS Code 存储惯例扫描
 * {@code ItemTable} 与 {@code cursorDiskKV} 两张 KV 表：行键或行值命中
 * api key / base url / sk- 令牌等特征时，尝试提取上游地址与密钥。
 * 解析不出任何可用配置时返回空清单（前端提示没有解析出供应商配置）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("cursor")
public class CursorConfigImportParser extends AbstractChannelConfigImportParser {

    /** 行键命中即参与提取（api/key/baseUrl/anthropic/openai 等特征词） */
    private static final Pattern KEY_HINT = Pattern.compile(
            "(?i)(api[_-]?key|base[_-]?url|anthropic|openai)");

    /** 单行值参与提取的长度上限（大 JSON 遥测行不做全量正则） */
    private static final int MAX_VALUE_SCAN = 512 * 1024;

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'`,<>\\\\]+");
    private static final Pattern SK_KEY_PATTERN = Pattern.compile("sk-[A-Za-z0-9_-]{8,}");

    @Override
    public List<ChannelImportEntry> parse(List<ChannelImportFile> files) {
        byte[] db = firstBytes(files);
        return withSqlite(db, this::scan);
    }

    /**
     * 扫描两张 KV 表，提取候选供应商行。
     */
    private List<ChannelImportEntry> scan(Connection conn) throws Exception {
        List<ChannelImportEntry> out = new ArrayList<>();
        for (String table : new String[] {"ItemTable", "cursorDiskKV"}) {
            if (!tableExists(conn, table)) {
                continue;
            }
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery("SELECT key, value FROM " + table)) {
                while (rs.next()) {
                    String rowKey = columnText(rs, "key");
                    String value = columnText(rs, "value");
                    if (value.length() > MAX_VALUE_SCAN) {
                        value = value.substring(0, MAX_VALUE_SCAN);
                    }
                    ChannelImportEntry entry = toEntry(table, rowKey, value);
                    if (entry != null) {
                        out.add(entry);
                    }
                }
            }
        }
        dedupeEntryNames(out);
        return out;
    }

    /**
     * 单行 → 导入草稿；不命中返回 null。
     */
    private ChannelImportEntry toEntry(String table, String rowKey, String value) {
        if (StringUtils.isEmpty(rowKey)) {
            return null;
        }
        boolean keyHit = KEY_HINT.matcher(rowKey).find();
        boolean valueHit = value != null && (value.contains("sk-") || keyHit);
        if (!keyHit && !valueHit) {
            return null;
        }
        String text = value == null ? "" : value;
        String baseUrl = "";
        Matcher urlMatcher = URL_PATTERN.matcher(text);
        if (urlMatcher.find()) {
            baseUrl = urlMatcher.group();
        }
        String apiKey = "";
        Matcher keyMatcher = SK_KEY_PATTERN.matcher(text);
        if (keyMatcher.find()) {
            apiKey = keyMatcher.group();
        }
        // 行键本身声明是 api key 且行值是单令牌（无空白）时，行值即密钥
        if (apiKey.isEmpty() && rowKey.toLowerCase().matches("(?i).*api[_-]?key.*")
                && !text.isEmpty() && !text.contains(" ") && !text.contains("\n")
                && text.length() >= 8 && text.length() <= 256) {
            apiKey = text.trim();
        }
        if (baseUrl.isEmpty() && apiKey.isEmpty()) {
            return null;
        }
        String hay = (rowKey + " " + text).toLowerCase();

        ChannelImportEntry entry = new ChannelImportEntry();
        entry.setKey("cursor:" + table + ":" + rowKey);
        entry.setSource("cursor");
        entry.setName(cursorName(rowKey));
        entry.setPlatform(hay.contains("anthropic") ? "anthropic" : "custom");
        entry.setBaseUrl(baseUrl);
        entry.setApiKey(apiKey);
        entry.setHasKey(!apiKey.isEmpty());
        entry.setModels(new ArrayList<>());
        if (baseUrl.isEmpty()) {
            entry.setSkip("仅识别到密钥，未识别到上游地址");
        }
        return entry;
    }

    /**
     * 行键 → 展示名（取最后一段并首字母大写，如 cursorAuth/accessToken → Accesstoken）。
     */
    private String cursorName(String rowKey) {
        String name = rowKey;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        if (name.isEmpty()) {
            name = rowKey;
        }
        if (name.length() > 40) {
            name = name.substring(0, 40);
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
