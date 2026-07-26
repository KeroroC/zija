package com.zija.inventory.internal.event;

import org.springframework.context.annotation.Configuration;

/**
 * 库存事件可靠投递配置。
 * <p>
 * Spring Modulith 2.0.5 + spring-modulith-starter-jdbc 默认启用：
 * <ul>
 *   <li>事务内把事件登记到 {@code event_publication} 表</li>
 *   <li>事务提交后由 {@code OrderedTransactionEventPublisher} 异步派发</li>
 * </ul>
 * 若后续需要事件外化（如发送到消息队列），在此声明 {@code EventExternalizationConfiguration}。
 */
@Configuration(proxyBeanMethods = false)
class InventoryEventConfig {
}
