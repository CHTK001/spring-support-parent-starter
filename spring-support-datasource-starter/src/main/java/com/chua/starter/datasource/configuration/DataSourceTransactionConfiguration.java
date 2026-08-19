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
/**
 * public class DataSourceTransactionConfiguration
 *
 * @author CH
 * @since 4.0.0.42
 */
@ConditionalOnClass(name = "org.springframework.jdbc.datasource.DataSourceTransactionManager")
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE + 10)
public class DataSourceTransactionConfiguration