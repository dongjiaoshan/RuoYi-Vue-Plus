package org.dromara.djs.warehouse.trace.pub.service;

import org.dromara.djs.warehouse.trace.pub.domain.vo.PublicTraceVo;

/**
 * 门店级猪肉动态追溯（公开只读，V7 row40）。
 *
 * <p>每个门店一个固定二维码 {@code /trace/store/<门店编码>}，扫出来的内容按「门店当天到店的猪肉货品里
 * 最重的那头猪」动态决定；选出耳号对应的追溯码后复用 {@link ITracePublicService#getByProduceCode}
 * 的猪肉追溯聚合，不另起一套。</p>
 */
public interface IStoreTracePublicService {

    /**
     * 按门店标识取当天的门店猪肉追溯。
     *
     * @param storeKey 门店标识（= {@code t_md_store.store_code}）
     * @return 门店不存在 → null；门店当天没有可追溯的猪肉货品 → {@code pig=null} 的空态 VO（只带门店块）；
     *         否则为选中那头猪的猪肉追溯 VO（{@code store} 恒为本门店）
     */
    PublicTraceVo getByStoreKey(String storeKey);
}
