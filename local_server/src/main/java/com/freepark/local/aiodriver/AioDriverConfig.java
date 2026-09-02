package com.freepark.local.aiodriver;

import java.util.ServiceLoader;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.freepark.driver.api.AIODriverFactory;
import com.freepark.driver.api.registry.AioDriverRegistry;

/**
 * 一体机驱动装配（平台侧接入骨架）。
 *
 * <p>依赖式驱动模块接入点：驱动包保持零框架依赖（可独立上传 Maven 仓库），
 * 厂商只要在自己的 jar 里通过 {@code META-INF/services/com.freepark.driver.api.AIODriverFactory}
 * 声明工厂实现，平台启动时即经 {@link ServiceLoader} 自动发现并注册进
 * {@link AioDriverRegistry}。以后接入新厂商 = 在 local_server/pom.xml 增加一个
 * 驱动依赖即可，无需改动本类或任何业务代码。
 *
 * <p>设备连接参数（品牌/地址/端口）跟随设备档案存于 parking_barrier，
 * 不经由本配置注入（见 AioDriverService）。
 */
@Configuration
public class AioDriverConfig {

    @Bean
    public AioDriverRegistry aioDriverRegistry() {
        AioDriverRegistry registry = new AioDriverRegistry();
        ServiceLoader.load(AIODriverFactory.class).forEach(registry::register);
        return registry;
    }
}
