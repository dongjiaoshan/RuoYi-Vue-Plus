package org.dromara.djs.warehouse.trace.pub.service;

import org.dromara.djs.warehouse.trace.pub.domain.vo.PublicTraceVo;

/**
 * 公开追溯聚合 Service（TRC-PUB-API-001）。
 *
 * <p>顾客扫码追溯的<b>唯一公开只读数据源</b>。按 {@code produce_code} 一次性聚合
 * 一条追溯码的全链路展示数据，复用 admin 详情聚合 + 跨域 JOIN（养殖生长/用药、
 * 种植地块/农事、有机证书、门店），返回 C 端裁剪 {@link PublicTraceVo}。</p>
 *
 * <p>只读，无写方法（追溯链不可篡改）。</p>
 *
 * @author djs
 * @since TRC-PUB-API-001
 */
public interface ITracePublicService {

    /**
     * 按追溯码（= produce_code）聚合公开追溯数据。
     *
     * <p>Redis ~10min 缓存（key {@code trace:public:{produceCode}}）。先查缓存命中即返，
     * 未命中聚合查库后写缓存。追溯码不存在返 {@code null}（controller 转友好响应，不抛栈）。</p>
     *
     * @param produceCode 追溯码（小程序码 scene 带入）
     * @return 聚合 VO；追溯码不存在返 null
     */
    PublicTraceVo getByProduceCode(String produceCode);
}
