package com.freepark.driver.zhensi.transport;

/**
 * 臻识设备命令通道抽象：把一条品牌命令实际送达物理设备。
 *
 * <p>驱动内部自用（不属于 driver-api 契约）：
 * <ul>
 *   <li>真实环境：{@link HttpZhensiCommandTransport}（HTTP POST 到设备）；</li>
 *   <li>测试环境：注入记录型实现，不触碰网络。</li>
 * </ul>
 */
public interface ZhensiCommandTransport {

    /** 发送一条命令到设备；失败抛 {@code AioDriverException}。 */
    void send(ZhensiCommand command);

    /** 探测设备在线。 */
    boolean ping();
}
