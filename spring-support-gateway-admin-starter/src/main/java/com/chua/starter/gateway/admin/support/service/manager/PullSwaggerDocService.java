/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chua.starter.gateway.admin.support.service.manager;

import java.util.Set;
import org.apache.shenyu.admin.model.bean.UpstreamInstance;

/**
 * pull service api document Manager.
 */
public interface PullSwaggerDocService {

    /**
     * pull API document.
     * @param currentServices currentServices
     */
    void pullApiDocument(Set<UpstreamInstance> currentServices);

    /**
     * pull API document.
     * @param instance instance
     */
    void pullApiDocument(UpstreamInstance instance);
}
