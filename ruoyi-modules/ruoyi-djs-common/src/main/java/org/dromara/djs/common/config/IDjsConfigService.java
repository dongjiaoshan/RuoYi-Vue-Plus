package org.dromara.djs.common.config;

/**
 * djs 业务配置版本聚合服务（CROSS-DICT-001）。
 *
 * <p>对"客户可调业务配置全集"（{@link ConfigTableConstants#CONFIG_TABLES}：生产周期 /
 * 育种 / 公猪精液 / 用药计划 config）算一个 SHA-256 版本号，供小程序业务页 onShow 探测：
 * 版本变了才重拉对应 config，避免工人拿着旧的出栏日龄 / 妊娠天数等参数干活。</p>
 *
 * <p>与 {@link org.dromara.djs.common.dict.IDjsDictService} 同构：都是"实时聚合 → 序列化
 * → SHA-256"的只读版本服务，<b>不做 djs 层缓存</b>，admin 改完配置即时反映。</p>
 *
 * @author djs
 * @since CROSS-DICT-001
 */
public interface IDjsConfigService {

    /**
     * 当前业务配置全集的 SHA-256 hash（64 字符小写 hex）。
     *
     * <p>每次实时聚合所有可调 config 表的业务参数列 → 序列化 → SHA-256，
     * <b>不做任何 TTL 缓存</b>：否则会出现"后台改完配置、version 被缓存 →
     * 小程序比对永远相等 → 永远不重拉"的坑（与 {@code IDjsDictService.currentVersion()}
     * 反缓存铁律一致）。version 是配置值的<b>纯函数</b>——改回原值 hash 也回到原值。</p>
     *
     * @return SHA-256 hex
     */
    String currentVersion();
}
