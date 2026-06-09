package org.dromara.djs.warehouse.vegdock.mapper;

import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.vegdock.domain.VegPurchase;
import org.dromara.djs.warehouse.vegdock.domain.vo.VegPurchaseCropGroupVo;
import org.dromara.djs.warehouse.vegdock.domain.vo.VegPurchaseVo;

import java.util.List;

/**
 * 外购果蔬到货 Mapper（FIX-WMS-MP-VEGDOCK-001）。
 *
 * @author djs
 * @since FIX-WMS-MP-VEGDOCK-001
 */
public interface VegPurchaseMapper extends BaseMapperPlus<VegPurchase, VegPurchaseVo> {

    /**
     * 外购收货 tab：按 crop 聚合未入完（status != 'done'）的到货，取「品种 + 待入库合计」（图 42）。
     *
     * <p>{@code tenant_id} 由 MP 多租户拦截器在 final SQL 阶段注入，应用层无需显式 WHERE。</p>
     */
    @Select("SELECT crop_id AS cropId,"
        + "       MAX(crop_name) AS cropName,"
        + "       COALESCE(SUM(pending_weight), 0) AS pendingWeight,"
        + "       COUNT(1) AS recordCount "
        + "  FROM t_warehouse_veg_purchase "
        + " WHERE del_flag = '0' AND status <> 'done' "
        + " GROUP BY crop_id "
        + " ORDER BY pendingWeight DESC, crop_id ASC")
    List<VegPurchaseCropGroupVo> selectCropGroups();

}
