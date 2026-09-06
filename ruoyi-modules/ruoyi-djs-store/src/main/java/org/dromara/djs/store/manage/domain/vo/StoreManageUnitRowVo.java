package org.dromara.djs.store.manage.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店管理月度看板 - 业态卡内一行（一个计量单位一行）（MGMT-MP-STORE-MONTH-001）。
 *
 * <p>同一业态下不同产品单位不可加总（白条按 kg、礼盒按盒、干货按份），故按
 * {@code t_warehouse_product_info.product_unit} 分行——原型「猪肉产品」卡两行（头 / KG）、
 * 「干货产品」卡两行（盒 / 份）即由此而来。</p>
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
@Data
public class StoreManageUnitRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 计量单位（产品主数据 product_unit 原值，如 kg / 份 / 盒）。 */
    private String unit;

    /** 需求量（门店当月下单量）。 */
    private StoreManageMetricVo demand;

    /** 销售量（门店盘点当月 sale_qty + gift_qty）。 */
    private StoreManageMetricVo sale;

    /** 退回量（门店退回记录当月 return_quantity）。 */
    private StoreManageMetricVo returned;

}
