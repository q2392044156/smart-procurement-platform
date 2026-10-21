package com.procuremind.platform.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.procuremind.platform.system.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

@Configuration
public class MybatisConfig {
    @Bean MybatisPlusInterceptor interceptor() {
        MybatisPlusInterceptor value = new MybatisPlusInterceptor();
        value.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        value.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return value;
    }
    @Bean MetaObjectHandler auditMetaHandler() {
        return new MetaObjectHandler() {
            public void insertFill(MetaObject metaObject) {
                strictInsertFill(metaObject, "createdAt", Instant.class, Instant.now());
                strictInsertFill(metaObject, "updatedAt", Instant.class, Instant.now());
                strictInsertFill(metaObject, "createdBy", Long.class, SecurityUtils.userIdOrSystem());
                strictInsertFill(metaObject, "updatedBy", Long.class, SecurityUtils.userIdOrSystem());
                strictInsertFill(metaObject, "version", Integer.class, 0);
                strictInsertFill(metaObject, "deleted", Integer.class, 0);
            }
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
                strictUpdateFill(metaObject, "updatedBy", Long.class, SecurityUtils.userIdOrSystem());
            }
        };
    }
}
