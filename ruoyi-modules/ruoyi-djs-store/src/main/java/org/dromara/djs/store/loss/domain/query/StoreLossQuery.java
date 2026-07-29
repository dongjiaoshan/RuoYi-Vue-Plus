package org.dromara.djs.store.loss.domain.query;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 门店损耗记录查询 query（DENGBO-R30）。
 *
 * <p>列表页（门店管理 > 门店损耗记录）筛选：损耗日期范围 + 产品名称模糊 + 产品类型多选 + 损耗类型精确。
 * 门店维度由 {@code StoreLineHandler}（{@code Current-Store-Id} 头）行级注入，不在 query 显式传。</p>
 *
 * @author djs
 * @since DENGBO-R30
 */
@Data
public class StoreLossQuery {

    /** 产品名称模糊（先按 {@code t_warehouse_product_info.product_name} 预查 productId 集再 IN）。 */
    private String productName;

    /**
     * 产品类型多选（字典 {@code djs_belong_type}）。
     *
     * <p>{@code belong_type} 不在损耗表上，service 先按产品主数据（{@code t_warehouse_product_info.belong_type}）
     * 预查 productId 集再 IN。选中「其他」({@code other}) 时一并命中 {@code belong_type IS NULL} 的外购商品。</p>
     */
    private List<String> belongTypes;

    /** 损耗类型精确（{@code djs_store_loss_type}：store_daily_loss / white_bar_split_loss）。 */
    private String lossType;

    /** 损耗日期下界（含）。 */
    private LocalDate lossDateFrom;

    /** 损耗日期上界（含）。 */
    private LocalDate lossDateTo;
}
