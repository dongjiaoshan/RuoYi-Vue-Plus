package org.dromara.djs.warehouse.vegdock.service;

import org.dromara.djs.warehouse.vegdock.domain.bo.VegPurchaseInboundBo;
import org.dromara.djs.warehouse.vegdock.domain.bo.VegPurchaseReceiveBo;
import org.dromara.djs.warehouse.vegdock.domain.vo.VegPurchaseCropGroupVo;
import org.dromara.djs.warehouse.vegdock.domain.vo.VegPurchaseVo;

import java.util.List;

/**
 * 外购果蔬月台收货 + 果蔬间入库 Service（FIX-WMS-MP-VEGDOCK-001）。
 *
 * @author djs
 * @since FIX-WMS-MP-VEGDOCK-001
 */
public interface IVegPurchaseService {

    /**
     * 外购收货登记（图 42 外购产品收货）：INSERT 一行到货记录（status=pending）。
     *
     * @return 新建记录 ID（snowflake）
     */
    Long receive(VegPurchaseReceiveBo bo);

    /**
     * 果蔬间入库确认（图 43 实际入库量）：累加 actual / 递减 pending / 推进 status，
     * 同事务 UPSERT location_stock + INSERT stock_flow。
     *
     * @return stock_flow.id（snowflake）
     */
    Long inbound(VegPurchaseInboundBo bo);

    /**
     * 外购收货 tab：按品种聚合的待入库列表（图 42 卡片）。
     */
    List<VegPurchaseCropGroupVo> listCropGroups();

    /**
     * 果蔬间入库：某品种下未入完（status != done）的到货明细行（图 43 下钻）。
     */
    List<VegPurchaseVo> listByCrop(Long cropId);

}
