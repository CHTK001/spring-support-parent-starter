package com.chua.starter.monitor.server.loader;

import com.chua.common.support.utils.IoUtils;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.loader.URLResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;

/**
 * @author CH
 */
public class CustomResourceLoader extends URLResourceLoader {
    @Override
    public synchronized Reader getResourceReader(String name, String encoding) throws ResourceNotFoundException {
        InputStream rawStream = null;
        try {
            URL u = new URL(name);
            URLConnection conn = u.openConnection();
            int timeout = getTimeout();
            if(timeout > 0) {
                conn.setConnectTimeout(timeout);
                conn.setReadTimeout(timeout);
            }
            rawStream = conn.getInputStream();
            return buildReader(rawStream, encoding);
        } catch (IOException e) {
            IoUtils.closeQuietly(rawStream);
        }
        return super.getResourceReader(name, encoding);
    }

    @Override
    public long getLastModified(Resource resource){
        return resource.getLastModified();
    }
}
