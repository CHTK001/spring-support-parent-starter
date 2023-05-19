package com.chua.starter.script.marker;

import com.chua.starter.script.ScriptExtension;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 编译器
 *
 * @author CH
 */
public class DelegateMarker implements Marker {

    static final Map<String, Marker> MARKER = new ConcurrentHashMap<>();

    static {
        List<Marker> markers = SpringFactoriesLoader.loadFactories(Marker.class, ClassLoader.getSystemClassLoader());
        for (Marker marker : markers) {
            ScriptExtension scriptExtension = marker.getClass().getDeclaredAnnotation(ScriptExtension.class);
            if (null == scriptExtension) {
                continue;
            }

            MARKER.put(scriptExtension.value().toUpperCase(), marker);
        }
    }

    private final Marker marker;

    public DelegateMarker(String suffix) {
        this.marker = MARKER.getOrDefault(suffix.toUpperCase(), this);
    }

    public Marker getMarker() {
        return marker;
    }

    @Override
    public Object getBean(String source) {
        return null;
    }
}
