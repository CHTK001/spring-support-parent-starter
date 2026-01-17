package com.chua.starter.minio.support.template;

import io.minio.MinioClient;

/**
 * client
 * @author CH
 */
public class PearlMinioClient extends MinioClient {

    public PearlMinioClient(MinioClient client) {
        super(client);
    }

}
