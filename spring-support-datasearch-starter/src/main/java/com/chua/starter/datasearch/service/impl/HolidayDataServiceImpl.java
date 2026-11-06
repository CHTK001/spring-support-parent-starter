// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasearch.service.impl;

import com.chua.common.support.datasearch.holiday.model.HolidayInfo;
import com.chua.common.support.datasearch.holiday.spi.HolidayProvider;
import com.chua.starter.datasearch.pojo.holiday.HolidayVO;
import com.chua.starter.datasearch.service.HolidayDataService;
import com.chua.starter.datasearch.support.SpiProviders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节假日数据服务实现。
 *
 * <p>逐个 {@code HolidayProvider} 取数，首个给出非空结果的即采用。同一日期可能被多个数据源
 * 覆盖（在线 JSON 与内置兜底会合并到同一张表），按日期去重后再排序，避免日历上出现两行同一天。</p>
 *
 * <p>年份缺省取当年：前端日历打开时就是「今年的放假安排」，让它显式传 year 只是精确查询的手段，
 * 不传不该返回空。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Service
public class HolidayDataServiceImpl implements HolidayDataService {

    /**
     * 年份合理区间下限（早于这个年份的放假安排没有公开数据源）
     */
    private static final int MIN_YEAR = 1900;

    /**
     * 年份合理区间上限（预留一个甲子的余量，同时挡住把毫秒时间戳误当 year 的入参）
     */
    private static final int MAX_YEAR = 2200;

    /**
     * 节假日主键起点（本模块无库表，前端只把它当稳定标识用）
     */
    private static final long ID_BASE = 1L;

    @Override
    public List<HolidayVO> list(Integer year) {
        int effectiveYear = normalizeYear(year);
        List<HolidayProvider> providers = SpiProviders.list(HolidayProvider.class);
        if (providers.isEmpty()) {
            log.warn("[holiday] 无可用节假日数据源");
            return null;
        }
        // 按日期去重：在线 JSON 与内置 2026 兜底会落到同一张表，同日多条会让日历重复渲染
        Map<LocalDate, HolidayInfo> merged = new LinkedHashMap<>();
        for (HolidayProvider provider : providers) {
            List<HolidayInfo> holidays;
            try {
                holidays = provider.getHolidays(effectiveYear);
            } catch (Exception e) {
                log.warn("[holiday] 数据源取数失败: provider={}, year={}, msg={}",
                        provider.name(), effectiveYear, e.getMessage());
                continue;
            }
            if (holidays == null) {
                continue;
            }
            for (HolidayInfo info : holidays) {
                if (info == null || info.getDate() == null) {
                    continue;
                }
                merged.putIfAbsent(info.getDate(), info);
            }
        }
        if (merged.isEmpty()) {
            return List.of();
        }
        List<LocalDate> dates = new ArrayList<>(merged.keySet());
        dates.sort(Comparator.naturalOrder());
        List<HolidayVO> result = new ArrayList<>(dates.size());
        long id = ID_BASE;
        for (LocalDate date : dates) {
            HolidayInfo info = merged.get(date);
            result.add(new HolidayVO(id++, effectiveYear, date.toString(),
                    text(info.getName()), info.isOffDay()));
        }
        return result;
    }

    /**
     * 归一入参年份：空或越界时取当年。
     *
     * @param year 入参
     * @return 生效年份
     */
    private int normalizeYear(Integer year) {
        int current = LocalDate.now().getYear();
        if (year == null || year < MIN_YEAR || year > MAX_YEAR) {
            return current;
        }
        return year;
    }

    /**
     * 名称兜底：数据源偶发给 null，前端见到空名会把该行整条丢弃。
     *
     * @param name 原始名称
     * @return 非空名称
     */
    private String text(String name) {
        return name == null || name.isBlank() ? "节假日" : name.trim();
    }
}
