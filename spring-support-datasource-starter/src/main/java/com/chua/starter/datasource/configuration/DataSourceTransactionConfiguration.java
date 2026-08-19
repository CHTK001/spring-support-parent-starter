/**
 * 事务
 *
 * @author CH
 * @since 2021-07-19
 */
@EnableConfigurationProperties({
        TransactionProperties.class,
        MultiDataSourceSettingProperties.class,
        MultiDataSourceProperties.class,
        DataSourceScriptProperties.class,
        MultiHikariDataSourceProperties.class,
})
@ConditionalOnClass(name = "org.springframework.jdbc.datasource.DataSourceTransactionManager")
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE + 10)
public class DataSourceTransactionConfiguration