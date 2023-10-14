package com.chua.starter.gen.support.loader;

import com.chua.common.support.utils.IoUtils;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.loader.URLResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;

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
