package com.chua.starter.rpc.support.protocol.bus;

import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.protocol.AbstractExporter;

import java.util.Map;

/**
 * bus
 *
 * @author CH
 */
public class BusExporter<T> extends AbstractExporter<T> {

    private final String key;

    private final Map<String, Exporter<?>> exporterMap;

    public BusExporter(Invoker<T> invoker, String key, Map<String, Exporter<?>> exporterMap) {
        super(invoker);
        this.key = key;
        this.exporterMap = exporterMap;
        exporterMap.put(key, this);
    }

    @Override
    public void afterUnExport() {
        exporterMap.remove(key, this);
    }

}
