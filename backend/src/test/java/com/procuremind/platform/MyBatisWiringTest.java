package com.procuremind.platform;

import com.procuremind.platform.mapper.RequisitionMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.mybatis.spring.annotation.MapperScan;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不连库，只验证 MyBatis 的自动配置能在这个 Spring Boot 版本上起来、
 * mapper 接口能注册成 bean。真正的 SQL 执行由集成测试覆盖。
 */
class MyBatisWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, MybatisAutoConfiguration.class))
            .withUserConfiguration(MapperRegistration.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:mysql://localhost:3306/procuremind",
                    "spring.datasource.username=procuremind",
                    "spring.datasource.password=procuremind123",
                    "mybatis.mapper-locations=classpath*:mapper/**/*.xml",
                    "mybatis.configuration.map-underscore-to-camel-case=true");

    @Test
    void mapperBeansAreRegistered() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RequisitionMapper.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @MapperScan("com.procuremind.platform.mapper")
    static class MapperRegistration {}
}
