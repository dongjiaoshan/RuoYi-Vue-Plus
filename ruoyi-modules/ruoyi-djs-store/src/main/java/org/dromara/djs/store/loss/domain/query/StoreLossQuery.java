package org.dromara.djs.store.loss.domain.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 门店损耗记录查询 query（DENGBO-R30）。
 *
 * <p>列表页（门店管理 > 门店损耗记录）筛选：损耗日期范围 + 产品名称模糊 + 损耗类型精确。
 * 门店维度由 {@code StoreLineHandler}（{@code Current-Store-Id} 头）行级注入，不在 query 显式传。</p>
 *
 * @author djs
 * @since DENGBO-R30
 */
@Data
public class StoreLossQuery {

    /** 产品名称模糊（LEFT JOIN {@code t_warehouse_product_info.product_name} 后内存过滤）。 */
    private String productName;

    /** 损耗类型精确（{@code djs_store_loss_type}：store_daily_loss / white_bar_split_loss）。 */
    private String lossType;

    /** 损耗日期下界（含）。 */
    private LocalDate lossDateFrom;

    /** 损耗日期上界（含）。 */
    private LocalDate lossDateTo;
}
