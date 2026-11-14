package com.chua.starter.monitor.support.server;

import com.chua.starter.monitor.support.server.model.ServerNodeSummary;
import com.chua.starter.monitor.support.server.model.ServerNodeView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 服务管理 —— 节点列表的过滤 / 排序 / 分页切片（纯函数）。
 *
 * <p>分页接口只做一件事：把全量视图按请求条件裁成当前页。过滤与统计留在这一层
 * 而不是下推到 SQL，是因为 {@code status} 是<b>运行时状态</b>（来自
 * {@link ServerNodeRegistry}），库里根本没有这一列；按状态筛选、按状态排序
 * 只能在装配完视图之后做。节点数量是「这台机器上挂了多少个监听服务」的量级，
 * 全量装配不会成为瓶颈。</p>
 *
 * <p>口径与前端 {@code utils/nodeList.ts} 对齐：关键字命中名称 / 协议编码 /
 * 协议描述 / 监听地址 / 端口 / 引擎 / 访问地址 / 报错文本；协议精确匹配；
 * 状态精确匹配。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
public final class ServerNodePager {

    /**
     * 每页条数上限：与前端 ReTable 的默认钳制一致，防止一次请求拉全表。
     */
    public static final int MAX_PAGE_SIZE = 500;

    /**
     * 状态排序权重：异常最先（排障优先），其次运行中，最后已停止。
     */
    private static final int STATUS_ERROR = 0;
    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_STOPPED = 2;
    private static final int STATUS_UNKNOWN = 9;

    private ServerNodePager() {
    }

    /**
     * 解析页码：缺省或非正数按第 1 页处理。
     *
     * @param page 原始页码
     * @return 不小于 1 的页码
     */
    public static int pageOf(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    /**
     * 解析每页条数：缺省按 20，超出 {@link #MAX_PAGE_SIZE} 时封顶。
     *
     * @param size 原始每页条数
     * @return 1 ~ {@link #MAX_PAGE_SIZE} 之间的条数
     */
    public static int sizeOf(Integer size) {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 按关键字 / 协议 / 状态过滤。
     *
     * @param views    全量视图
     * @param keyword  关键字，空则不过滤
     * @param protocol 协议编码，空则不过滤
     * @param status   运行状态，空则不过滤
     * @return 命中条件的视图（保持原顺序）
     */
    public static List<ServerNodeView> filter(List<ServerNodeView> views,
                                              String keyword,
                                              String protocol,
                                              String status) {
        List<ServerNodeView> result = new ArrayList<>();
        if (views == null) {
            return result;
        }
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String proto = protocol == null ? "" : protocol.trim();
        String state = status == null ? "" : status.trim();
        for (ServerNodeView view : views) {
            if (view == null) {
                continue;
            }
            if (!proto.isEmpty() && !proto.equals(view.protocol())) {
                continue;
            }
            if (!state.isEmpty() && !state.equals(view.status())) {
                continue;
            }
            if (!kw.isEmpty() && !searchableText(view).contains(kw)) {
                continue;
            }
            result.add(view);
        }
        return result;
    }

    /**
     * 按名称 / 端口 / 状态排序。
     *
     * @param views 视图列表（原地排序，调用方传入副本）
     * @param sort  排序口径：name（缺省）/ port / status
     * @return 同一个列表
     */
    public static List<ServerNodeView> sort(List<ServerNodeView> views, String sort) {
        if (views == null) {
            return new ArrayList<>();
        }
        Comparator<ServerNodeView> comparator;
        if ("port".equalsIgnoreCase(sort == null ? "" : sort.trim())) {
            comparator = Comparator.comparingInt(view -> view.port() == null ? 0 : view.port());
        } else if ("status".equalsIgnoreCase(sort == null ? "" : sort.trim())) {
            comparator = Comparator
                    .comparingInt((ServerNodeView view) -> statusOrder(view.status()))
                    .thenComparing(view -> text(view.name()), String.CASE_INSENSITIVE_ORDER);
        } else {
            comparator = Comparator.comparing(view -> text(view.name()), String.CASE_INSENSITIVE_ORDER);
        }
        views.sort(comparator);
        return views;
    }

    /**
     * 取当前页切片。
     *
     * @param list 已过滤排序的列表
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     * @param <T>  元素类型
     * @return 当前页的元素；页码越界时返回空列表，不抛异常
     */
    public static <T> List<T> slice(List<T> list, int page, int size) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        long from = (long) (page - 1) * size;
        if (from >= list.size()) {
            return new ArrayList<>();
        }
        int start = (int) from;
        int end = (int) Math.min(from + size, list.size());
        return new ArrayList<>(list.subList(start, end));
    }

    /**
     * 统计摘要。
     *
     * @param views 全量视图
     * @return 计数与名称表
     */
    public static ServerNodeSummary summarize(List<ServerNodeView> views) {
        long running = 0;
        long stopped = 0;
        long error = 0;
        long unknown = 0;
        long plugins = 0;
        List<String> names = new ArrayList<>();
        if (views != null) {
            for (ServerNodeView view : views) {
                if (view == null) {
                    continue;
                }
                String status = view.status() == null ? "" : view.status();
                switch (status) {
                    case "RUNNING" -> running++;
                    case "STOPPED" -> stopped++;
                    case "ERROR" -> error++;
                    default -> unknown++;
                }
                plugins += view.filters() == null ? 0 : view.filters().size();
                if (view.name() != null && !view.name().isBlank()) {
                    names.add(view.name());
                }
            }
        }
        long total = running + stopped + error + unknown;
        return new ServerNodeSummary(total, running, stopped, error, unknown, plugins, names);
    }

    /**
     * 参与关键字匹配的文本（小写）。
     *
     * <p>刻意包含 {@code error}：节点异常时用户最想搜的就是报错关键字
     * （如端口号、BindException），只搜名称等于把排障入口堵死。</p>
     *
     * @param view 节点视图
     * @return 小写化的匹配文本
     */
    private static String searchableText(ServerNodeView view) {
        return String.join(" ",
                text(view.name()),
                text(view.protocol()),
                text(view.protocolDesc()),
                text(view.host()),
                view.port() == null ? "" : String.valueOf(view.port()),
                text(view.engine()),
                text(view.serverUrl()),
                text(view.error())).toLowerCase(Locale.ROOT);
    }

    /**
     * 状态排序权重。
     *
     * @param status 运行状态
     * @return 权重，越小越靠前
     */
    private static int statusOrder(String status) {
        if (status == null) {
            return STATUS_UNKNOWN;
        }
        return switch (status) {
            case "ERROR" -> STATUS_ERROR;
            case "RUNNING" -> STATUS_RUNNING;
            case "STOPPED" -> STATUS_STOPPED;
            default -> STATUS_UNKNOWN;
        };
    }

    /**
     * 空安全取文本。
     *
     * @param value 原值
     * @return 非 null 的文本
     */
    private static String text(String value) {
        return value == null ? "" : value;
    }
}
