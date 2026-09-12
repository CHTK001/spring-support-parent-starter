package com.chua.starter.common.support.xml;

import com.chua.common.support.lang.json.JsonObject;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * XML 工具本地适配器。
 *
 * @since 4.0.0.42
 * @author CH
 */
public class Xml {

    /** 创建 Xml 实例 */
    private Xml() {
    }

    /**
      * 将 json对象 转换为 XML 字节数组。
     *
     * @param jsonObject JSON 对象
     * @return XML 字节数组
     */
    public static byte[] toXmlByte(JsonObject jsonObject) {
        if (jsonObject == null) {
            return new byte[0];
        }
        String json = jsonObject.toJSONString();
        if (json == null || json.isEmpty()) {
            return new byte[0];
        }
        String xml = jsonToXml(json);
        return xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * json转为xml。
     * @param json json
     * @return json转为xml的结果
     */
    private static String jsonToXml(String json) {
        try {
            StringBuilder xml = new StringBuilder();
            xml.append("<xml>");
            json = json.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1).trim();
                if (!json.isEmpty()) {
                    xml.append(convertJsonObject(json));
                }
            }
            xml.append("</xml>");
            return xml.toString();
        } catch (Exception e) {
            return "<xml><error>" + escapeXml(json) + "</error></xml>";
        }
    }

    /**
     * 转换json对象。
     * @param json json
     * @return 转换json对象的结果
     */
    private static String convertJsonObject(String json) {
        StringBuilder xml = new StringBuilder();
        int i = 0;
        while (i < json.length()) {
            /**
             * 跳过whitespace。
             * @param json json
             * @param i i
             */
            skipWhitespace(json, i);
            int colon = findNextColon(json, i);
            if (colon < 0) {
                break;
            }
            String key = parseStringValue(json, i, colon);
            if (key == null) {
                break;
            }
            xml.append("<").append(escapeXml(key)).append(">");
            int valueStart = colon + 1;
            int comma = findValueEnd(json, valueStart);
            String value = json.substring(valueStart, comma).trim();
            if (value.startsWith("{")) {
                xml.append(convertJsonObject(value.substring(1, value.length() - 1).trim()));
            } else if (value.startsWith("[")) {
                xml.append(convertJsonArray(value));
            } else {
                String parsed = parseStringValue(json, valueStart, comma);
                if (parsed != null) {
                    xml.append(escapeXml(parsed));
                } else {
                    xml.append(escapeXml(value));
                }
            }
            xml.append("</").append(escapeXml(key)).append(">");
            i = comma + 1;
        }
        return xml.toString();
    }

    /**
     * 转换jsonarray。
     * @param array array
     * @return 转换jsonarray的结果
     */
    private static String convertJsonArray(String array) {
        StringBuilder xml = new StringBuilder();
        int i = 1;
        while (i < array.length() - 1) {
            /**
             * 跳过whitespace。
             * @param array array
             * @param i i
             */
            skipWhitespace(array, i);
            char c = array.charAt(i);
            if (c == ']') {
                break;
            }
            if (c == '{') {
                int end = findMatchingBrace(array, i);
                String obj = array.substring(i + 1, end);
                xml.append("<item>").append(convertJsonObject(obj)).append("</item>");
                i = end + 1;
            } else {
                int comma = array.indexOf(',', i);
                int endBracket = array.indexOf(']', i);
                int end = comma > 0 && comma < endBracket ? comma : endBracket;
                String val = array.substring(i, end).trim();
                if (!val.isEmpty()) {
                    String parsed = parseStringValue(val, 0, val.length());
                    xml.append("<item>").append(escapeXml(parsed != null ? parsed : val)).append("</item>");
                }
                i = end + 1;
            }
            /**
             * 跳过whitespace。
             * @param array array
             * @param i i
             */
            skipWhitespace(array, i);
            if (i < array.length() && array.charAt(i) == ',') {
                i++;
            }
        }
        return xml.toString();
    }

    /**
      * 查找下一个colon。
     * @param json json
     * @param start 启动
     * @return find下一个colon的结果
     */
    private static int findNextColon(String json, int start) {
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ':') {
                return i;
            }
            if (c == '"') {
                i = skipString(json, i);
            }
            i++;
        }
        return -1;
    }

    /**
      * 查找价值观结束。
     * @param json json
     * @param start 启动
     * @return find价值观结束的结果
     */
    private static int findValueEnd(String json, int start) {
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ',') {
                int[] j = {i - 1};
                /**
                 * 跳过whitespacebackward。
                 * @param json json
                 * @param j j
                 */
                skipWhitespaceBackward(json, j);
                if (j[0] >= start && json.charAt(j[0]) == '}') {
                    return i;
                }
                if (j[0] >= start && json.charAt(j[0]) == ']') {
                    return i;
                }
            }
            if (c == '"') {
                i = skipString(json, i);
            }
            i++;
        }
        return json.length();
    }

    /**
     * 解析字符串价值观。
     * @param json json
     * @param start 启动
     * @param end 结束
     * @return 解析字符串价值观的结果
     */
    private static String parseStringValue(String json, int start, int end) {
        String sub = json.substring(start, end).trim();
        if (sub.startsWith("\"") && sub.endsWith("\"") && sub.length() >= 2) {
            return unescapeJsonString(sub.substring(1, sub.length() - 1));
        }
        return null;
    }

    /**
     * 跳过字符串。
     * @param json json
     * @param start 启动
     * @return 跳过字符串的结果
     */
    private static int skipString(String json, int start) {
        int i = start + 1;
        while (i < json.length()) {
            if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                i += 2;
                continue;
            }
            if (json.charAt(i) == '"') {
                return i;
            }
            i++;
        }
        return json.length();
    }

    /**
     * 跳过whitespace。
     * @param s s
     * @param i i
     * @return 跳过whitespace的结果
     */
    private static int skipWhitespace(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * 跳过whitespacebackward。
     * @param s s
     * @param i i
     */
    private static void skipWhitespaceBackward(String s, int[] i) {
        while (i[0] >= 0 && Character.isWhitespace(s.charAt(i[0]))) {
            i[0]--;
        }
    }

    /**
      * 查找匹配brace。
     * @param s s
     * @param start 启动
     * @return find匹配brace的结果
     */
    private static int findMatchingBrace(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            } else if (c == '"') {
                i = skipString(s, i);
            }
        }
        return s.length() - 1;
    }

    /**
      * escapexml。
     * @param s s
     * @return escapeXml的结果
     */
    private static String escapeXml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * unescapejson字符串。
     * @param s s
     * @return unescapejson字符串的结果
     */
    private static String unescapeJsonString(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"' -> { sb.append('"'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case '/' -> { sb.append('/'); i++; }
                    case 'b' -> { sb.append('\b'); i++; }
                    case 'f' -> { sb.append('\f'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
