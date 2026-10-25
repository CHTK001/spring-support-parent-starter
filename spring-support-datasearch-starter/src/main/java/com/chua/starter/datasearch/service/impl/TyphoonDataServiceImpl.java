package com.chua.starter.datasearch.service.impl;

import com.chua.common.support.datasearch.typhoon.model.TyphoonActivity;
import com.chua.common.support.datasearch.typhoon.model.TyphoonDetail;
import com.chua.common.support.datasearch.typhoon.model.TyphoonForecast;
import com.chua.common.support.datasearch.typhoon.model.TyphoonForecastPoint;
import com.chua.common.support.datasearch.typhoon.model.TyphoonPoint;
import com.chua.common.support.datasearch.typhoon.spi.TyphoonProvider;
import com.chua.starter.datasearch.pojo.typhoon.TyphoonDetailVO;
import com.chua.starter.datasearch.pojo.typhoon.TyphoonItemVO;
import com.chua.starter.datasearch.service.TyphoonDataService;
import com.chua.starter.datasearch.support.SpiProviders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 顶栏台风数据服务实现。
 *
 * <p>数据源经 SPI 装载，列表语义遵循 SPI 契约：{@code null} 表示数据源不可用（继续下一个源，
 * 全部不可用时返回空让前端回落），空列表表示数据源确认当前无活跃台风。
 * 风圈报文 {@code "250|150|250|150"} 在此解析成四方位数组，多机构预报按机构拆成多条通道下发。</p>
 *
 * <p>出参是前端 {@code TyphoonItem / TyphoonDetail} 的唯一契约，而前端对后台数据
 * 原样透传、不做补齐，因此数据源给出的字段在此全部映射：英文名、报文时间、风力等级、
 * 预警级别、移动方向与移动速度、三级风圈，缺一项顶栏弹窗就少一行信息。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Service
public class TyphoonDataServiceImpl implements TyphoonDataService {

    /**
     * 风圈方位数（东北、东南、西南、西北）
    */
    private static final int RADIUS_DIRECTIONS = 4;

    /**
     * 优先采用的预报机构名关键字
    */
    private static final String PREFERRED_FORECAST_AGENCY = "中国";

    /**
     * 本系统时间口径，与天气契约 {@code yyyy-MM-ddTHH:mm} 一致
    */
    private static final DateTimeFormatter CANON_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    /**
     * 日期段在文本中的最大下标，用于判定文本是否带时区偏移
    */
    private static final int DATE_SEGMENT_LIMIT = 9;

    /**
     * 纯日期文本（{@code yyyy-MM-dd}）的长度
    */
    private static final int DATE_TEXT_LENGTH = 10;

    @Override
    public List<TyphoonItemVO> activeTyphoons() {
        for (TyphoonProvider provider : SpiProviders.list(TyphoonProvider.class)) {
            List<TyphoonActivity> activities = provider.getActiveTyphoons();
            if (activities == null) {
                continue;
            }
            List<TyphoonItemVO> result = new ArrayList<>(activities.size());
            for (TyphoonActivity activity : activities) {
                TyphoonItemVO vo = toItem(activity, provider.name());
                if (vo != null) {
                    result.add(vo);
                }
            }
            return result;
        }
        return null;
    }

    @Override
    public TyphoonDetailVO detail(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String tfid = id.trim();
        for (TyphoonProvider provider : SpiProviders.list(TyphoonProvider.class)) {
            TyphoonDetail detail = provider.getTyphoon(tfid);
            if (detail == null) {
                continue;
            }
            TyphoonDetailVO vo = toDetail(detail, tfid, provider.name());
            if (!vo.path().isEmpty() || !vo.forecast().isEmpty()) {
                return vo;
            }
        }
        return null;
    }

    /**
     * 活跃项实体 → 契约。
     *
     * <p>数据源给出的字段全部透出（前端原样消费，不做补齐）：英文名、报文时间、
     * 风力等级、预警级别、移动方向与移动速度缺一，顶栏弹窗就少一行。</p>
     *
     * @param activity 实体
     * @param source   数据源名
     * @return 契约出参；编号缺失时返回 空
     */
    private TyphoonItemVO toItem(TyphoonActivity activity, String source) {
        if (activity == null || activity.getTfid() == null || activity.getTfid().isBlank()) {
            return null;
        }
        return new TyphoonItemVO(
                activity.getTfid(),
                activity.getName(),
                activity.getEnname(),
                activity.getStrong(),
                num(activity.getLat()),
                num(activity.getLng()),
                canonTime(activity.getTime()),
                activity.getTimeformate(),
                num(activity.getSpeed()),
                num(activity.getPressure()),
                num(activity.getPower()),
                activity.getWarnlevel(),
                activity.getMovedirection(),
                num(activity.getMovespeed()),
                source);
    }

    /**
     * 详情实体 → 契约。
     *
     * @param detail 实体
     * @param source 数据源名
     * @return 契约出参
     */
    private TyphoonDetailVO toDetail(TyphoonDetail detail, String fallbackId, String source) {
        List<TyphoonPoint> points = detail.getPoints() == null ? List.of() : detail.getPoints();
        String strong = points.isEmpty() ? null : points.getFirst().getStrong();
        List<TyphoonDetailVO.Track> path = new ArrayList<>(points.size());
        for (TyphoonPoint point : points) {
            path.add(toTrack(point.getTime(), point.getLng(), point.getLat(), point.getStrong(),
                    point.getSpeed(), point.getPressure(), point.getRadius7(), point.getRadius10(),
                    point.getRadius12()));
        }
        String tfid = detail.getTfid() == null || detail.getTfid().isBlank() ? fallbackId : detail.getTfid();
        return new TyphoonDetailVO(
                tfid,
                detail.getName(),
                strong,
                detail.getWarnlevel(),
                path,
                toForecast(points),
                source);
    }

    /**
     * 收集全部机构预报通道。
     *
     * <p>数据源在同一路径点上并列给出 中国 / 中国台湾 / 日本 / 中国香港 / 美国 多家通道，
     * 前端地图按机构各画一条预测线，因此这里不再收敛成单条：按机构名去重（同名取首个），
     * 并保证「中国」排第一（前端把 {@code forecast[0]} 当主线接在历史末点上）。
     * 同一权重（如 日本、美国）保持数据源原有顺序——{@link List#sort} 是稳定排序。</p>
     *
     * @param points 历史路径点
     * @return 预报通道列表；无预报时返回空列表
     */
    private List<TyphoonDetailVO.Forecast> toForecast(List<TyphoonPoint> points) {
        TyphoonPoint host = pickForecastHost(points);
        if (host == null) {
            return List.of();
        }
        Map<String, TyphoonForecast> sources = new LinkedHashMap<>();
        for (TyphoonForecast source : host.getForecast()) {
            if (source == null || source.getForecastpoints() == null || source.getForecastpoints().isEmpty()) {
                continue;
            }
            String name = source.getTm() == null ? "" : source.getTm().trim();
            if (!name.isEmpty()) {
                sources.putIfAbsent(name, source);
            }
        }
        if (sources.isEmpty()) {
            return List.of();
        }
        List<TyphoonForecast> ordered = new ArrayList<>(sources.values());
        ordered.sort(Comparator.comparingInt((TyphoonForecast source) -> agencyRank(source.getTm())));
        List<TyphoonDetailVO.Forecast> result = new ArrayList<>(ordered.size());
        for (TyphoonForecast source : ordered) {
            List<TyphoonForecastPoint> forecastPoints = source.getForecastpoints();
            List<TyphoonDetailVO.Track> track = new ArrayList<>(forecastPoints.size());
            for (TyphoonForecastPoint point : forecastPoints) {
                track.add(toTrack(point.getTime(), point.getLng(), point.getLat(), point.getStrong(),
                        point.getSpeed(), point.getPressure(), null, null, null));
            }
            result.add(new TyphoonDetailVO.Forecast(source.getTm(), track));
        }
        return result;
    }

    /**
     * 取最近一个带可用预报的历史点。
     *
     * <p>数据源把当次发布的预报挂在对应历史点上，而最新点可能只有实况还没挂预报，
     * 因此从末点往前找第一个含非空机构通道的点，与前端兜底链路的取点口径一致。</p>
     *
     * @param points 历史路径点
     * @return 带预报的路径点；全部没有时返回 空
     */
    private TyphoonPoint pickForecastHost(List<TyphoonPoint> points) {
        for (int i = points.size() - 1; i >= 0; i--) {
            TyphoonPoint point = points.get(i);
            if (point == null || point.getForecast() == null) {
                continue;
            }
            for (TyphoonForecast source : point.getForecast()) {
                if (source != null && source.getForecastpoints() != null && !source.getForecastpoints().isEmpty()) {
                    return point;
                }
            }
        }
        return null;
    }

    /**
     * 机构排序权重：中国台优先（主线），其次中国台湾，其余保持源顺序。
     *
     * @param name 机构名
     * @return 权重（越小越靠前）
     */
    private int agencyRank(String name) {
        if (PREFERRED_FORECAST_AGENCY.equals(name)) {
            return 0;
        }
        return name != null && name.contains("台湾") ? 1 : 2;
    }

    /**
     * 组装路径点。
     *
     * @param time     时间
     * @param lng      经度文本
     * @param lat      纬度文本
     * @param strong   强度等级
     * @param speed    风速文本
     * @param pressure 气压文本
     * @param radius7  7 级风圈报文
     * @param radius10 10 级风圈报文
     * @param radius12 12 级风圈报文
     * @return 路径点
     */
    private TyphoonDetailVO.Track toTrack(String time, String lng, String lat, String strong,
                                          String speed, String pressure, String radius7, String radius10,
                                          String radius12) {
        return new TyphoonDetailVO.Track(
                canonTime(time),
                num(lng),
                num(lat),
                strong,
                num(speed),
                num(pressure),
                toRadii(radius7),
                toRadii(radius10),
                toRadii(radius12));
    }

    /**
     * 三方时间报文 → 本系统口径（本地 {@code yyyy-MM-ddTHH:mm}）。
     *
     * <p>同一数据源内部并不统一：活跃列表回带时区偏移的 UTC 串
     * （{@code 2026-09-21T00:00:00.000+00:00}），路径点回本地无偏移串
     * （{@code 2026-09-21 08:00:00}）。若原样透传，顶栏气泡里同一台风的两处时间
     * 会相差一个时区。带偏移的按偏移换算到本机时区，不带的视为本地时间。</p>
     *
     * @param raw 原始报文
     * @return 统一口径时间；空白返回 空，无法解析时原样返回文本
     */
    private String canonTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        try {
            if (text.endsWith("Z") || text.lastIndexOf('+') > DATE_SEGMENT_LIMIT || text.lastIndexOf('-') > DATE_SEGMENT_LIMIT) {
                ZonedDateTime local = OffsetDateTime.parse(text).atZoneSameInstant(ZoneId.systemDefault());
                return local.format(CANON_TIME);
            }
            String iso = text.replace(' ', 'T');
            if (iso.length() <= DATE_TEXT_LENGTH) {
                return LocalDate.parse(iso).atStartOfDay().format(CANON_TIME);
            }
            return LocalDateTime.parse(iso).format(CANON_TIME);
        } catch (DateTimeParseException e) {
            return text;
        }
    }

    /**
     * 风圈报文 → 四方位半径数组 [东北, 东南, 西南, 西北]。
     *
     * <p>兼容「管道分隔 4 段」「单一数值（四方位同半径）」两种形态；
     * 全零或无法解析时返回空，由前端不画该级风圈。</p>
     *
     * @param raw 原始报文
     * @return 四方位半径；无有效数据时返回 空
     */
    private List<Double> toRadii(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<Double> radii = new ArrayList<>(RADIUS_DIRECTIONS);
        double sum = 0D;
        if (raw.contains("|")) {
            String[] parts = raw.split("\\|");
            for (int i = 0; i < RADIUS_DIRECTIONS; i++) {
                double value = i < parts.length ? numOrZero(parts[i]) : 0D;
                radii.add(value);
                sum += value;
            }
        } else {
            double value = numOrZero(raw);
            for (int i = 0; i < RADIUS_DIRECTIONS; i++) {
                radii.add(value);
            }
            sum = value;
        }
        return sum <= 0D ? null : radii;
    }

    /**
     * 文本转数值。
     *
     * @param value 文本
     * @return 数值；空白或非法时返回 空
     */
    private Double num(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 文本转数值，非法取 0。
     *
     * @param value 文本
     * @return 数值
     */
    private double numOrZero(String value) {
        Double parsed = num(value);
        return parsed == null ? 0D : parsed;
    }
}

