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

package com.chua.starter.gateway.admin.support.config;

import com.chua.starter.gateway.admin.support.mybatis.og.interceptor.OpenGaussSQLPrepareInterceptor;
import com.chua.starter.gateway.admin.support.mybatis.og.interceptor.OpenGaussSQLQueryInterceptor;
import com.chua.starter.gateway.admin.support.mybatis.og.interceptor.OpenGaussSqlUpdateInterceptor;
import com.chua.starter.gateway.admin.support.mybatis.oracle.OracleSQLPrepareInterceptor;
import com.chua.starter.gateway.admin.support.mybatis.oracle.OracleSQLUpdateInterceptor;
import com.chua.starter.gateway.admin.support.mybatis.pg.interceptor.PostgreSQLPrepareInterceptor;
import com.chua.starter.gateway.admin.support.mybatis.pg.interceptor.PostgreSQLQueryInterceptor;
import com.chua.starter.gateway.admin.support.mybatis.pg.interceptor.PostgreSqlUpdateInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 *  for MyBatis Configure management.
 */
@Configuration
public class MapperConfig {

    /**
     * The type PostgreSQL.
     */
    @Configuration
    @ConditionalOnProperty(name = "shenyu.database.dialect", havingValue = "postgresql")
    static class PostgreSQLConfig {

        /**
         * Add the plugin to the MyBatis plugin interceptor chain.
         *
         * @return {@linkplain PostgreSQLQueryInterceptor}
         */
        @Bean
        @ConditionalOnMissingBean(PostgreSQLQueryInterceptor.class)
        public PostgreSQLQueryInterceptor postgreSqlQueryInterceptor() {
            return new PostgreSQLQueryInterceptor();
        }

        /**
         * Add the plugin to the MyBatis plugin interceptor chain.
         *
         * @return {@linkplain PostgreSQLPrepareInterceptor}
         */
        @Bean
        @ConditionalOnMissingBean(PostgreSQLPrepareInterceptor.class)
        public PostgreSQLPrepareInterceptor postgreSqlPrepareInterceptor() {
            return new PostgreSQLPrepareInterceptor();
        }

        /**
         * Add the plugin to the MyBatis plugin interceptor chain.
         *
         * @return {@linkplain PostgreSqlUpdateInterceptor}
         */
        @Bean
        @ConditionalOnMissingBean(PostgreSqlUpdateInterceptor.class)
        public PostgreSqlUpdateInterceptor postgreSqlUpdateInterceptor() {
            return new PostgreSqlUpdateInterceptor();
        }
    }

    /**
     * The type OracleSQL.
     */
    @Configuration
    @ConditionalOnProperty(name = "shenyu.database.dialect", havingValue = "oracle")
    static class OracleSQLConfig {

        /**
         * Add the plugin to the MyBatis plugin interceptor chain.
         *
         * @return {@linkplain OracleSQLPrepareInterceptor}
         */
        @Bean
        @ConditionalOnMissingBean(OracleSQLPrepareInterceptor.class)
        public OracleSQLPrepareInterceptor oracleSqlPrepareInterceptor() {
            return new OracleSQLPrepareInterceptor();
        }

        /**
         * Add the plugin to the MyBatis plugin interceptor chain.
         *
         * @return {@linkplain OracleSQLUpdateInterceptor}
         */
        @Bean
        @ConditionalOnMissingBean(OracleSQLUpdateInterceptor.class)
        public OracleSQLUpdateInterceptor oracleSqlUpdateInterceptor() {
            return new OracleSQLUpdateInterceptor();
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "shenyu.database.dialect", havingValue = "opengauss")
    static class OpenGaussSQLConfig {

        /**
         * Add the plugin to the MyBatis plugin interceptor chain.
         *
         * @return {@linkplain OpenGaussSQLQueryInterceptor}
         */
        @Bean
        @ConditionalOnMissingBean(OpenGaussSQLQueryInterceptor.class)
        public OpenGaussSQLQueryInterceptor openGaussSqlQueryInterceptor() {
            return new OpenGaussSQLQueryInterceptor();
        }

        /**
         * Add the plugin to the MyBatis plugin interceptor chain.
         *
         * @return {@linkplain OpenGaussSQLPrepareInterceptor}
         */
        @Bean
        @ConditionalOnMissingBean(OpenGaussSQLPrepareInterceptor.class)
        public OpenGaussSQLPrepareInterceptor openGaussSqlPrepareInterceptor() {
            return new OpenGaussSQLPrepareInterceptor();
        }

        /**
         * Add the plugin to the MyBatis plugin interceptor chain.
         *
         * @return {@linkplain OpenGaussSqlUpdateInterceptor}
         */
        @Bean
        @ConditionalOnMissingBean(OpenGaussSqlUpdateInterceptor.class)
        public OpenGaussSqlUpdateInterceptor openGaussSqlUpdateInterceptor() {
            return new OpenGaussSqlUpdateInterceptor();
        }
    }
}
