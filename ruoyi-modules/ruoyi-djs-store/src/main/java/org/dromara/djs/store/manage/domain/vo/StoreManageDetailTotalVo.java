package org.dromara.djs.store.manage.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店管理「明细」页顶部按单位的三项合计（V6-R180）。
 *
 * <p>数值不另写 SQL，直接取业态卡那三条聚合方法的结果，所以合计与卡片数字构造上一致
 * ——甲方点进明细第一眼就能拿它对上刚才那张卡。</p>
 *
 * @author djs
 * @since V6-R180
 */
@Data
public class StoreManageDetailTotalVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 计量单位（kg / 份 / 盒 …；档案未填单位为「未设单位」）。 */
    private String unit;

    /** 该单位下的需求量合计（= 业态卡同单位行的「需求量」）。 */
    private BigDecimal demandQty;

    /** 该单位下的销售量合计（= 业态卡同单位行的「销售量」）。 */
    private BigDecimal saleQty;

    /** 该单位下的退回量合计（= 业态卡同单位行的「退回量」）。 */
    private BigDecimal returnQty;
}
