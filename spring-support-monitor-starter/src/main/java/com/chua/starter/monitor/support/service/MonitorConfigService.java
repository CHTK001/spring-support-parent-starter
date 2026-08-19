package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitorConfig;

import java.util.List;
import java.util.Map;

/** @author CH */
public interface MonitorConfigService {
    ReturnResult<MsMonitorConfig> findByKey(String key);
    String getString(String key);
    String getString(String key, String defaultValue);
    int getInt(String key, int defaultValue);
    long getLong(String key, long defaultValue);
    boolean getBoolean(String key, boolean defaultValue);
    ReturnResult<Map<String, String>> getAll();
    List<MsMonitorConfig> listAll();
    ReturnResult<MsMonitorConfig> upsert(MsMonitorConfig entity);
}
