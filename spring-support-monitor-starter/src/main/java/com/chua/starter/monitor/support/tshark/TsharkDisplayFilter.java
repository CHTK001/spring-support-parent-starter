package com.chua.starter.monitor.support.tshark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 抓包报文的显示过滤（常用字段子集）。
 *
 * <p>tshark 的显示过滤语法非常庞大，但页面上的分析场景集中在「协议 / IP / 端口 /
 * HTTP 方法 / 响应码 / 文本」这几类。本类把这类表达式解析成带 {@code ?} 占位符的
 * SQL 条件，交给 MyBatis-Plus 的 {@code apply(sql, args)} 执行，既保留参数化查询，
 * 又不必为每个字段单独写查询方法。</p>
 *
 * <p><strong>为什么不直接对 pcap 跑 {@code tshark -Y}</strong>：报文已经解析入库，
 * 列表分页、统计、Top 流都要基于同一份过滤结果；每次翻页都扫一遍 pcap 在几十万包的
 * 会话上会非常慢。这里覆盖常用字段，遇到不支持的字段明确报错，而不是静默返回空结果。</p>
 *
 * <p>支持：</p>
 * <ul>
 *   <li>协议名：{@code http} / {@code dns} / {@code tls} / {@code tcp} / {@code udp} 等</li>
 *   <li>字段：{@code ip.addr} / {@code ip.src} / {@code ip.dst} / {@code tcp.port} /
 *       {@code udp.port} / {@code port} / {@code frame.len} / {@code frame.number} /
 *       {@code frame contains} / {@code http.request.method} / {@code http.response.code} /
 *       {@code http.request.uri} / {@code dns.qry.name} / {@code tcp.flags.syn} 等</li>
 *   <li>逻辑：{@code and / or / not}（也接受 {@code && / || / !}）与括号</li>
 *   <li>比较：{@code == / != / > / >= / < / <= / contains}</li>
 *   <li>纯文本：不认识的裸词按关键字在协议 / IP / 摘要 / 还原文本里模糊匹配</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
public final class TsharkDisplayFilter {

    private static final Set<String> PROTOCOLS = Set.of(
            "http", "http2", "https", "tls", "ssl", "dns", "ssh", "ftp", "smtp",
            "imap", "pop", "telnet", "mqtt", "smb", "quic", "dhcp", "ntp",
            "icmp", "icmpv6", "arp", "tcp", "udp", "ip", "ipv6");

    private TsharkDisplayFilter() {
    }

    /**
     * 解析结果：SQL 片段 + 参数。
     *
     * @param sql  形如 {@code (LOWER(ms_tshark_packet_protocol) = ?)} 的条件
     * @param args 与占位符一一对应的参数
     */
    public record SqlFilter(String sql, List<Object> args) {
        /**
         * empty
         * @return 返回值
        */
        public static SqlFilter empty() {
            return new SqlFilter("", List.of());
        }

        /**
         * 判断是否为空
         * @return 返回值
        */
        public boolean isEmpty() {
            return sql == null || sql.isBlank();
        }
    }

    /**
     * 解析显示过滤表达式。
     *
     * @param expression 用户输入
     * @return SQL 条件；空表达式返回 {@link SqlFilter#empty()}
     * @throws IllegalArgumentException 表达式语法错误或字段不支持
     */
    public static SqlFilter parse(String expression) {
        if (expression == null || expression.isBlank()) {
            return SqlFilter.empty();
        }
        Parser parser = new Parser(expression);
        Node root = parser.parseExpression();
        parser.expectEnd();
        SqlBuilder out = new SqlBuilder();
        root.render(out);
        return new SqlFilter(out.sql.toString(), Collections.unmodifiableList(out.args));
    }

    // ==================== 解析 ====================

    private interface Node {
        void render(SqlBuilder out);
    }

    private record AndNode(Node left, Node right) implements Node {
        @Override
        public void render(SqlBuilder out) {
            out.sql.append('(');
            left.render(out);
            out.sql.append(" AND ");
            right.render(out);
            out.sql.append(')');
        }
    }

    private record OrNode(Node left, Node right) implements Node {
        @Override
        public void render(SqlBuilder out) {
            out.sql.append('(');
            left.render(out);
            out.sql.append(" OR ");
            right.render(out);
            out.sql.append(')');
        }
    }

    private record NotNode(Node child) implements Node {
        @Override
        public void render(SqlBuilder out) {
            out.sql.append("NOT (");
            child.render(out);
            out.sql.append(')');
        }
    }

    private record KeywordNode(String value) implements Node {
        @Override
        public void render(SqlBuilder out) {
            String like = "%" + value.toLowerCase(Locale.ROOT) + "%";
            out.sql.append("(LOWER(ms_tshark_packet_protocol) LIKE ?")
                    .append(" OR LOWER(ms_tshark_packet_source_ip) LIKE ?")
                    .append(" OR LOWER(ms_tshark_packet_dest_ip) LIKE ?")
                    .append(" OR LOWER(ms_tshark_packet_info) LIKE ?")
                    .append(" OR LOWER(ms_tshark_packet_restored) LIKE ?");
            out.args.add(like);
            out.args.add(like);
            out.args.add(like);
            out.args.add(like);
            out.args.add(like);
            if (value.chars().allMatch(Character::isDigit)) {
                try {
                    int port = Integer.parseInt(value);
                    out.sql.append(" OR ms_tshark_packet_source_port = ?")
                            .append(" OR ms_tshark_packet_dest_port = ?");
                    out.args.add(port);
                    out.args.add(port);
                } catch (NumberFormatException ignore) {
                    // 超长数字按文本处理即可
                }
            }
            out.sql.append(')');
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int pos;

        /**
         * Parser
         * @param source 字符串
         * @return 返回值
        */
        Parser(String source) {
            this.tokens = new Lexer(source).lex();
        }

        Node parseExpression() {
            return parseOr();
        }

        void expectEnd() {
            if (peek().type != TokenType.EOF) {
                throw error("无法识别的表达式片段「" + peek().text + "」");
            }
        }

        /**
         * parse Or
         * @return 返回值
        */
        private Node parseOr() {
            Node left = parseAnd();
            while (matchWord("or") || matchOp("||")) {
                left = new OrNode(left, parseAnd());
            }
            return left;
        }

        /**
         * parse And
         * @return 返回值
        */
        private Node parseAnd() {
            Node left = parseUnary();
            while (matchWord("and") || matchOp("&&")) {
                left = new AndNode(left, parseUnary());
            }
            return left;
        }

        /**
         * parse Unary
         * @return 返回值
        */
        private Node parseUnary() {
            if (matchWord("not") || matchOp("!")) {
                return new NotNode(parseUnary());
            }
            if (match(TokenType.LPAREN)) {
                Node node = parseOr();
                expect(TokenType.RPAREN, "缺少右括号");
                return node;
            }
            return parsePrimary();
        }

        /**
         * parse Primary
         * @return 返回值
        */
        private Node parsePrimary() {
            Token left = advance();
            if (left.type == TokenType.EOF) {
                throw error("表达式不完整");
            }
            if (!isOperand(left)) {
                throw error("无法识别「" + left.text + "」");
            }

            String op = null;
            if (peek().type == TokenType.OP) {
                op = advance().text;
            } else if (peek().type == TokenType.WORD
                    && ("contains".equalsIgnoreCase(peek().text)
                    || "matches".equalsIgnoreCase(peek().text))) {
                op = advance().text.toLowerCase(Locale.ROOT);
            }

            if (op == null) {
                return operandAsNode(left);
            }

            Token right = advance();
            if (!isOperand(right)) {
                throw error("「" + left.text + " " + op + "」后缺少值");
            }
            return comparison(left.text, op, right.text);
        }

        /**
         * operand As Node
         * @param token 参数
         * @return 返回值
        */
        private Node operandAsNode(Token token) {
            String text = token.text == null ? "" : token.text.trim();
            if (text.isEmpty()) {
                throw error("过滤值为空");
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (PROTOCOLS.contains(lower)) {
                return protocolNode(lower);
            }
            return new KeywordNode(text);
        }

        /**
         * protocol Node
         * @param protocol 字符串
         * @return 返回值
        */
        private Node protocolNode(String protocol) {
            return out -> {
                switch (protocol) {
                    case "tcp" -> out.sql.append("(LOWER(ms_tshark_packet_protocol) IN (")
                            .append("'tcp','http','tls','ssh','ftp','smtp','smb','mqtt','telnet'))");
                    case "udp" -> out.sql.append("(LOWER(ms_tshark_packet_protocol) IN (")
                            .append("'udp','dns','dhcp','ntp','quic'))");
                    case "ip" -> out.sql.append("(ms_tshark_packet_source_ip IS NOT NULL OR ")
                            .append("ms_tshark_packet_dest_ip IS NOT NULL)");
                    case "ipv6" -> out.sql.append("(ms_tshark_packet_source_ip LIKE '%:%' OR ")
                            .append("ms_tshark_packet_dest_ip LIKE '%:%')");
                    case "https", "ssl" -> out.sql.append("(LOWER(ms_tshark_packet_protocol) IN ('tls','https'))");
                    case "http2" -> out.sql.append("(LOWER(ms_tshark_packet_protocol) = 'http' OR ")
                            .append("LOWER(ms_tshark_packet_info) LIKE 'http/2%')");
                    case "icmpv6" -> out.sql.append("LOWER(ms_tshark_packet_protocol) = 'icmp'");
                    default -> {
                        // 协议名要做「包含」匹配：tshark 只把最外层协议写进 protocol 列，
                        // 例如明文 HTTP 常落在 TCP 上，只比对 protocol 会让「http」查出 0 条，
                        // 用户会以为过滤坏了。因此再补一层 info 摘要模糊匹配；http 另加常用端口兜底。
                        out.sql.append("(LOWER(ms_tshark_packet_protocol) = ? OR ")
                                .append("LOWER(ms_tshark_packet_info) LIKE ?");
                        out.args.add(protocol);
                        out.args.add("%" + protocol + "%");
                        if ("http".equals(protocol)) {
                            out.sql.append(" OR ms_tshark_packet_source_port IN (80,8000,8080)")
                                    .append(" OR ms_tshark_packet_dest_port IN (80,8000,8080)");
                        }
                        out.sql.append(')');
                    }
                }
            };
        }

        /**
         * comparison
         * @param fieldRaw 字符串
         * @param opRaw 字符串
         * @param valueRaw 字符串
         * @return 返回值
        */
        private Node comparison(String fieldRaw, String opRaw, String valueRaw) {
            String field = fieldRaw == null ? "" : fieldRaw.trim().toLowerCase(Locale.ROOT);
            String op = opRaw == null ? "==" : opRaw.trim().toLowerCase(Locale.ROOT);
            String value = valueRaw == null ? "" : valueRaw.trim();
            if ("matches".equals(op)) {
                throw error("暂不支持 matches（正则）过滤，请改用 contains 或关键字");
            }
            if ("=".equals(op)) {
                op = "==";
            }

            // 没有字段名的「frame contains / http contains」按全文匹配处理
            if (field.equals("frame") || field.equals("tcp") || field.equals("udp")
                    || field.equals("http") || field.equals("data")) {
                if ("contains".equals(op) || "==".equals(op)) {
                    return fullTextNode(value);
                }
                throw error("字段 " + field + " 只支持 contains / == 文本匹配");
            }

            return switch (field) {
                case "ip.addr", "ipv6.addr", "ip.host" -> addrNode(value, op);
                case "ip.src", "ipv6.src" -> stringColumnNode("ms_tshark_packet_source_ip", value, op);
                case "ip.dst", "ipv6.dst" -> stringColumnNode("ms_tshark_packet_dest_ip", value, op);
                case "tcp.port", "udp.port", "port" -> portNode(value, op, null);
                case "tcp.srcport", "udp.srcport" -> portNode(value, op, "ms_tshark_packet_source_port");
                case "tcp.dstport", "udp.dstport" -> portNode(value, op, "ms_tshark_packet_dest_port");
                case "frame.len" -> numberNode("ms_tshark_packet_length", value, op);
                case "frame.number" -> numberNode("ms_tshark_packet_frame_no", value, op);
                case "http.request.method" -> methodNode(value, op);
                case "http.response.code" -> statusNode(value, op);
                case "http.request.uri" -> likeNode("ms_tshark_packet_info", value, op);
                case "http.host", "http.user_agent", "http.cookie", "http.referer" ->
                        throw error("字段 " + field + " 未入库，暂不支持；可改用 frame contains 关键字");
                case "dns.qry.name" -> likeNode("ms_tshark_packet_info", value, op);
                case "tcp.flags", "tcp.flags.syn", "tcp.flags.ack", "tcp.flags.fin",
                     "tcp.flags.reset", "tcp.flags.rst", "tcp.flags.psh" ->
                        flagsNode(field, value, op);
                default -> {
                    if (field.contains(".")) {
                        throw error("不支持的显示过滤字段「" + fieldRaw + "」；支持协议名、ip.addr、"
                                + "ip.src/dst、tcp/udp.port、frame.len/number、frame contains、"
                                + "http.request.method、http.response.code 等常用字段");
                    }
                    throw error("无法识别的过滤字段「" + fieldRaw + "」");
                }
            };
        }

        /**
         * full Text Node
         * @param value 字符串
         * @return 返回值
        */
        private Node fullTextNode(String value) {
            return out -> {
                String like = "%" + value.toLowerCase(Locale.ROOT) + "%";
                out.sql.append("(LOWER(ms_tshark_packet_info) LIKE ? OR ")
                        .append("LOWER(ms_tshark_packet_restored) LIKE ?)");
                out.args.add(like);
                out.args.add(like);
            };
        }

        /**
         * addr Node
         * @param op 字符串
         * @param value 字符串
         * @return 返回值
        */
        private Node addrNode(String value, String op) {
            return out -> {
                String lowered = value.toLowerCase(Locale.ROOT);
                switch (op) {
                    case "==" -> {
                        out.sql.append("(ms_tshark_packet_source_ip = ? OR ms_tshark_packet_dest_ip = ?)");
                        out.args.add(value);
                        out.args.add(value);
                    }
                    case "!=" -> {
                        out.sql.append("(ms_tshark_packet_source_ip <> ? AND ms_tshark_packet_dest_ip <> ?)");
                        out.args.add(value);
                        out.args.add(value);
                    }
                    case "contains" -> {
                        String like = "%" + lowered + "%";
                        out.sql.append("(LOWER(ms_tshark_packet_source_ip) LIKE ? OR ")
                                .append("LOWER(ms_tshark_packet_dest_ip) LIKE ?)");
                        out.args.add(like);
                        out.args.add(like);
                    }
                    default -> throw error("IP 字段只支持 == / != / contains");
                }
            };
        }

        /**
         * string Column Node
         * @param column 字符串
         * @param op 字符串
         * @param value 字符串
         * @return 返回值
        */
        private Node stringColumnNode(String column, String value, String op) {
            return out -> {
                if ("contains".equals(op)) {
                    out.sql.append("LOWER(").append(column).append(") LIKE ?");
                    out.args.add("%" + value.toLowerCase(Locale.ROOT) + "%");
                } else if ("==".equals(op) || "!=".equals(op)) {
                    out.sql.append("LOWER(").append(column).append(')')
                            .append("==".equals(op) ? " = ?" : " <> ?");
                    out.args.add(value.toLowerCase(Locale.ROOT));
                } else {
                    throw error("文本字段只支持 == / != / contains");
                }
            };
        }

        /**
         * port Node
         * @param column 字符串
         * @param op 字符串
         * @param value 字符串
         * @return 返回值
        */
        private Node portNode(String value, String op, String column) {
            int port;
            try {
                port = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw error("端口必须是数字：" + value);
            }
            return out -> {
                if (column != null) {
                    numberNode(column, value, op).render(out);
                    return;
                }
                if ("==".equals(op)) {
                    out.sql.append("(ms_tshark_packet_source_port = ? OR ms_tshark_packet_dest_port = ?)");
                    out.args.add(port);
                    out.args.add(port);
                } else if ("!=".equals(op)) {
                    out.sql.append("(ms_tshark_packet_source_port <> ? AND ms_tshark_packet_dest_port <> ?)");
                    out.args.add(port);
                    out.args.add(port);
                } else if (Set.of(">", ">=", "<", "<=").contains(op)) {
                    out.sql.append("(ms_tshark_packet_source_port ").append(op).append(" ? OR ")
                            .append("ms_tshark_packet_dest_port ").append(op).append(" ?)");
                    out.args.add(port);
                    out.args.add(port);
                } else {
                    throw error("端口字段只支持 == / != / > / >= / < / <=");
                }
            };
        }

        /**
         * number Node
         * @param column 字符串
         * @param op 字符串
         * @param value 字符串
         * @return 返回值
        */
        private Node numberNode(String column, String value, String op) {
            long number;
            try {
                number = Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw error("字段需要数字值：" + value);
            }
            return out -> {
                if (!Set.of("==", "!=", ">", ">=", "<", "<=").contains(op)) {
                    throw error("数字字段只支持 == / != / > / >= / < / <=");
                }
                out.sql.append(column).append(' ')
                        .append("==".equals(op) ? "=" : op).append(" ?");
                out.args.add(number);
            };
        }

        /**
         * method Node
         * @param op 字符串
         * @param value 字符串
         * @return 返回值
        */
        private Node methodNode(String value, String op) {
            String method = value.toLowerCase(Locale.ROOT);
            return out -> {
                if ("contains".equals(op)) {
                    out.sql.append("LOWER(ms_tshark_packet_info) LIKE ?");
                    out.args.add("%" + method + "%");
                } else if ("==".equals(op)) {
                    out.sql.append("LOWER(ms_tshark_packet_info) LIKE ?");
                    out.args.add(method + " %");
                } else if ("!=".equals(op)) {
                    out.sql.append("NOT (LOWER(ms_tshark_packet_info) LIKE ?)");
                    out.args.add(method + " %");
                } else {
                    throw error("HTTP 方法只支持 == / != / contains");
                }
            };
        }

        /**
         * status Node
         * @param op 字符串
         * @param value 字符串
         * @return 返回值
        */
        private Node statusNode(String value, String op) {
            String code = value.toLowerCase(Locale.ROOT);
            return out -> {
                if ("contains".equals(op) || "==".equals(op)) {
                    out.sql.append("LOWER(ms_tshark_packet_info) LIKE ?");
                    out.args.add("% " + code + "%");
                } else if ("!=".equals(op)) {
                    out.sql.append("NOT (LOWER(ms_tshark_packet_info) LIKE ?)");
                    out.args.add("% " + code + "%");
                } else {
                    throw error("HTTP 状态码只支持 == / != / contains");
                }
            };
        }

        /**
         * like Node
         * @param column 字符串
         * @param op 字符串
         * @param value 字符串
         * @return 返回值
        */
        private Node likeNode(String column, String value, String op) {
            String like = "%" + value.toLowerCase(Locale.ROOT) + "%";
            return out -> {
                if ("contains".equals(op) || "==".equals(op)) {
                    out.sql.append("LOWER(").append(column).append(") LIKE ?");
                    out.args.add(like);
                } else if ("!=".equals(op)) {
                    out.sql.append("NOT (LOWER(").append(column).append(") LIKE ?)");
                    out.args.add(like);
                } else {
                    throw error("该字段只支持 == / != / contains");
                }
            };
        }

        /**
         * flags Node
         * @param field 字符串
         * @param op 字符串
         * @param value 字符串
         * @return 返回值
        */
        private Node flagsNode(String field, String value, String op) {
            boolean enabled;
            if ("0".equals(value) || "false".equalsIgnoreCase(value)) {
                enabled = false;
            } else if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
                enabled = true;
            } else {
                throw error("TCP 标志位只支持 == 0 / == 1");
            }
            String marker = switch (field) {
                case "tcp.flags.syn" -> "[SYN";
                case "tcp.flags.ack" -> "ACK";
                case "tcp.flags.fin" -> "[FIN";
                case "tcp.flags.reset", "tcp.flags.rst" -> "[RST";
                case "tcp.flags.psh" -> "[PSH";
                default -> throw error("tcp.flags 的复合值暂不支持，请使用 tcp.flags.syn/fin/reset/ack");
            };
            // tcp.flags.syn == 1 → 匹配 SYN；== 0 → 排除；!= 1 → 排除；!= 0 → 匹配
            boolean negate = "==".equals(op) ? !enabled : enabled;
            return out -> {
                if (negate) {
                    out.sql.append("NOT (ms_tshark_packet_info LIKE ?)");
                } else {
                    out.sql.append("ms_tshark_packet_info LIKE ?");
                }
                out.args.add("%" + marker + "%");
            };
        }

        /**
         * is Operand
         * @param token 参数
         * @return 返回值
        */
        private boolean isOperand(Token token) {
            return token.type == TokenType.WORD || token.type == TokenType.STRING;
        }

        /**
         * match
         * @param type 参数
         * @return 返回值
        */
        private boolean match(TokenType type) {
            if (peek().type == type) {
                pos++;
                return true;
            }
            return false;
        }

        /**
         * match Word
         * @param word 字符串
         * @return 返回值
        */
        private boolean matchWord(String word) {
            if (peek().type == TokenType.WORD && word.equalsIgnoreCase(peek().text)) {
                pos++;
                return true;
            }
            return false;
        }

        /**
         * match Op
         * @param op 字符串
         * @return 返回值
        */
        private boolean matchOp(String op) {
            if (peek().type == TokenType.OP && op.equals(peek().text)) {
                pos++;
                return true;
            }
            return false;
        }

        /**
         * expect
         * @param message 字符串
         * @param type 参数
         * @return 返回值
        */
        private void expect(TokenType type, String message) {
            if (!match(type)) {
                throw error(message);
            }
        }

        /**
         * peek
         * @return 返回值
        */
        private Token peek() {
            return tokens.get(Math.min(pos, tokens.size() - 1));
        }

        /**
         * advance
         * @return 返回值
        */
        private Token advance() {
            Token token = peek();
            if (pos < tokens.size() - 1) {
                pos++;
            }
            return token;
        }

        /**
         * error
         * @param message 字符串
         * @return 返回值
        */
        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("显示过滤：" + message);
        }
    }

    // ==================== 词法 ====================

    private enum TokenType { WORD, STRING, OP, LPAREN, RPAREN, EOF }

    private record Token(TokenType type, String text) {
    }

    private static final class Lexer {
        private final String source;
        private int pos;

        /**
         * Lexer
         * @param source 字符串
         * @return 返回值
        */
        Lexer(String source) {
            this.source = source;
        }

        List<Token> lex() {
            List<Token> out = new ArrayList<>();
            while (pos < source.length()) {
                char c = source.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                    continue;
                }
                if (c == '(') {
                    out.add(new Token(TokenType.LPAREN, "("));
                    pos++;
                    continue;
                }
                if (c == ')') {
                    out.add(new Token(TokenType.RPAREN, ")"));
                    pos++;
                    continue;
                }
                if (c == '"' || c == '\'') {
                    out.add(new Token(TokenType.STRING, readQuoted(c)));
                    continue;
                }
                if (c == '&' && peekNext('&')) {
                    out.add(new Token(TokenType.OP, "&&"));
                    pos += 2;
                    continue;
                }
                if (c == '|' && peekNext('|')) {
                    out.add(new Token(TokenType.OP, "||"));
                    pos += 2;
                    continue;
                }
                if (c == '!' && !peekNext('=')) {
                    out.add(new Token(TokenType.OP, "!"));
                    pos++;
                    continue;
                }
                if (c == '=' || c == '!' || c == '>' || c == '<') {
                    String op;
                    if ((c == '=' || c == '!' || c == '>' || c == '<') && pos + 1 < source.length()
                            && source.charAt(pos + 1) == '=') {
                        op = "" + c + '=';
                        pos += 2;
                    } else if (c == '=') {
                        op = "==";
                        pos++;
                    } else {
                        op = String.valueOf(c);
                        pos++;
                    }
                    out.add(new Token(TokenType.OP, op));
                    continue;
                }
                out.add(new Token(TokenType.WORD, readWord()));
            }
            out.add(new Token(TokenType.EOF, ""));
            return out;
        }

        /**
         * peek Next
         * @param expected 参数
         * @return 返回值
        */
        private boolean peekNext(char expected) {
            return pos + 1 < source.length() && source.charAt(pos + 1) == expected;
        }

        /**
         * read Quoted
         * @param quote 参数
         * @return 返回值
        */
        private String readQuoted(char quote) {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < source.length()) {
                char c = source.charAt(pos++);
                if (c == '\\' && pos < source.length()) {
                    sb.append(source.charAt(pos++));
                } else if (c == quote) {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("显示过滤：引号没有闭合");
        }

        /**
         * read Word
         * @return 返回值
        */
        private String readWord() {
            int start = pos;
            while (pos < source.length()) {
                char c = source.charAt(pos);
                if (Character.isWhitespace(c) || c == '(' || c == ')'
                        || c == '=' || c == '!' || c == '>' || c == '<'
                        || (c == '&' && peekNext('&')) || (c == '|' && peekNext('|'))) {
                    break;
                }
                pos++;
            }
            if (pos == start) {
                // 单个 & / | 不是合法运算符，按普通字符吞掉，交给解析器报错
                pos++;
            }
            return source.substring(start, pos);
        }
    }

    // ==================== SQL 输出 ====================

    private static final class SqlBuilder {
        final StringBuilder sql = new StringBuilder();
        final List<Object> args = new ArrayList<>();
    }
}
