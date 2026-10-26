package org.dromara.djs.warehouse.cutout.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 猪肉日分割出库统计 列表查询参数（V7-R6）。
 *
 * <p>甲方搜索项 = 统计日期（区间，默认当天，默认值由前端给）+ 出库去向 + 出库门店。</p>
 *
 * <p>出库去向下拉 = {@code djs_stock_out_dest} 字典项 + 一个约定值 {@link #OUT_DEST_STORE_DEMAND}
 * （「门店需求」）。选字典项 = 只看仓库出库那一类；选约定值 = 只看门店需求那一类。
 * 约定值不在字典里（字典 value 全是业务去向码，没有 {@code store_demand}），撞不上。</p>
 *
 * @author djs
 * @since V7-R6
 */
@Data
public class CutOutDailyQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 出库去向下拉里「门店需求」的约定值；列表行 destType=store 时 outDest 也回这个值。 */
    public static final String OUT_DEST_STORE_DEMAND = "store_demand";

    /** 统计日期起（含，可空 = 不限；yyyy-MM-dd，全局 String→Date 转换器解析）。 */
    private Date beginDate;

    /**
     * 统计日期止（含，可空 = 不限）。SQL 里按 {@code < endDate + 1 天} 比：
     * 出库时间是 DATETIME，写 {@code <=} 会漏掉当天带时分秒的记录。
     */
    private Date endDate;

    /** 出库去向：{@code djs_stock_out_dest} 字典值，或 {@link #OUT_DEST_STORE_DEMAND}；空 = 全部。 */
    private String outDest;

    /** 出库门店（t_md_store.id）；只作用于门店需求那一类，选了门店就不再出仓库出库行。 */
    private Long storeId;
}
