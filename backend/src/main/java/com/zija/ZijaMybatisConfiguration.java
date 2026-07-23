package com.zija;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p>注册 MybatisPlusInterceptor，按以下顺序添加内部拦截器：</p>
 * <ol>
 *   <li>乐观锁拦截器（{@link OptimisticLockerInnerInterceptor}）——用于元数据实体（物品、位置、提醒规则等）的版本控制</li>
 *   <li>分页拦截器（{@link PaginationInnerInterceptor}）——适配 PostgreSQL 方言，必须最后注册以确保正确拦截</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
public class ZijaMybatisConfiguration {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
