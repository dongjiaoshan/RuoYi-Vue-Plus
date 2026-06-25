package org.dromara.djs.warehouse.product.service;

import org.dromara.djs.warehouse.product.domain.bo.GiftBoxBo;
import org.dromara.djs.warehouse.product.domain.vo.GiftBoxVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 礼盒组件 Service（WMS-MD-002）。
 *
 * @author djs
 * @since WMS-MD-002
 */
public interface IGiftBoxService {

    /**
     * 查询礼盒主体下所有组件（注入 {@code componentProductName} 便于展示）。
     */
    List<GiftBoxVo> queryByBoxId(Long boxProductId);

    /**
     * 批量查多个礼盒的组件清单（礼盒打包卡片「需要什么产品、需要多少」展示用，避免前端 N+1）。
     *
     * @param boxProductIds 礼盒产品 id 集合
     * @return boxProductId → 组件清单（含 componentProductName / componentCount / componentUnit）；无组件的盒不入 map
     */
    Map<Long, List<GiftBoxVo>> queryByBoxIds(Collection<Long> boxProductIds);

    /**
     * 批量新增组件（service 端会强制覆盖每条 BO 的 {@code boxProductId}）。
     *
     * @return 实际插入行数
     */
    int insertBatch(Long boxProductId, List<GiftBoxBo> components);

    /**
     * 软删该礼盒主体下所有组件（编辑场景"覆盖式 replace"前先调）。
     *
     * @return 实际软删行数
     */
    int softDeleteByBoxId(Long boxProductId);

    /**
     * 软删指定 id 集合（批量级联使用）。
     */
    int softDeleteByIds(Collection<Long> ids);

}
