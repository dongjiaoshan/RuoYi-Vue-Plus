package org.dromara.djs.warehouse.cutout.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 猪肉日分割出库统计 列表行（V7-R6）。
 *
 * <p>一行 = 一天 × 一个去向：仓库出库按出库去向（字典）一行，门店需求按门店一行。
 * {@code (statDate, destType, outDest, storeId)} 是行身份，也是「查看」下钻的分组键。</p>
 *
 * @author djs
 * @since V7-R6
 */
@Data
public class CutOutDailyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计日期（出库操作日期）。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date statDate;

    /** 行类别：warehouse 仓库出库 / store 门店需求。 */
    private String destType;

    /**
     * 出库去向原始值：仓库出库 = djs_stock_out_dest 字典值（去向为空的那一桶是空串）；
     * 门店需求 = 约定值 {@code store_demand}。下钻传它，不传 {@link #outDestLabel}。
     */
    private String outDest;

    /** 出库去向展示值（字典 label / 「门店需求」/ 「未指定」）。 */
    private String outDestLabel;

    /** 出库门店 ID（仅门店需求行）。 */
    private Long storeId;

    /** 出库门店名称（仅门店需求行；仓库出库行为空，前端显示「—」）。 */
    private String storeName;

    /** 出库总重量（kg）。 */
    private BigDecimal totalWeight;
}
