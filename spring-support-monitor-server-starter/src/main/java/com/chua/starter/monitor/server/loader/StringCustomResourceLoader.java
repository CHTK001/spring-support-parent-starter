package com.chua.starter.monitor.server.loader;

import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.loader.URLResourceLoader;

import java.io.Reader;
import java.io.StringReader;

/**
 * @author CH
 */
public class StringCustomResourceLoader extends URLResourceLoader {
    @Override
    public synchronized Reader getResourceReader(String name, String encoding) throws ResourceNotFoundException {
        return new StringReader(name);
    }

    @Override
    public long getLastModified(Resource resource){
        return resource.getLastModified();
    }
}
