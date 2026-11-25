package com.procuremind.platform;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不连数据库地校验 MyBatis 映射：XML 可解析、namespace 与接口一致、
 * 每个接口方法都有对应的 statement。真实的 SQL 语义仍由集成测试覆盖。
 */
class MapperXmlTest {

    private static final String MAPPER_PACKAGE = "com.procuremind.platform.mapper";

    @Test
    void everyMapperXmlParsesAndBindsEveryMethod() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        Resource[] resources = resolver.getResources("classpath*:mapper/**/*.xml");
        assertThat(resources).as("应该在 resources/mapper 下找到映射文件").isNotEmpty();
        for (Resource resource : resources) {
            try (var in = resource.getInputStream()) {
                new XMLMapperBuilder(in, configuration, resource.toString(), configuration.getSqlFragments()).parse();
            }
        }

        List<Class<?>> mappers = scanMapperInterfaces(resolver);
        assertThat(mappers).as("mapper 包下应该有接口").isNotEmpty();
        List<String> missing = new ArrayList<>();
        for (Class<?> mapper : mappers) {
            for (Method method : mapper.getDeclaredMethods()) {
                String statementId = mapper.getName() + "." + method.getName();
                if (!configuration.hasStatement(statementId)) missing.add(statementId);
            }
        }
        assertThat(missing).as("接口方法缺少对应的 XML statement").isEmpty();
    }

    private List<Class<?>> scanMapperInterfaces(PathMatchingResourcePatternResolver resolver) throws Exception {
        List<Class<?>> result = new ArrayList<>();
        for (Resource resource : resolver.getResources("classpath*:" + MAPPER_PACKAGE.replace('.', '/') + "/*.class")) {
            String filename = resource.getFilename();
            if (filename == null || filename.contains("$")) continue;
            Class<?> type = Class.forName(MAPPER_PACKAGE + "." + filename.substring(0, filename.length() - 6));
            if (type.isInterface()) result.add(type);
        }
        return result;
    }
}
