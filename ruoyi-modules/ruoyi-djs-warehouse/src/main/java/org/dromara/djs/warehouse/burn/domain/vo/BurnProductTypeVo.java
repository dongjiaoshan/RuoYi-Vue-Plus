package org.dromara.djs.warehouse.burn.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 燎毛入库产品类型 VO（D12X-MP-BURN-IA-001 mp 入库子页用）。
 *
 * <p>白条入库需按产品类型「整只 / 半只 / 猪头 / 猪蹄」分别入库（客户 §2.3）。
 * 本 VO = {@code t_warehouse_product_info} 中 {@code belong_type='white_bar'} 且
 * {@code product_id LIKE 'PROD-WHITE-BAR-%'} 的标准 4 行（排除测试数据）。
 * mp 入库子页每类型一行，录入该类型重量 → 提交时 productTypeItems.productId 指向所选类型。</p>
 *
 * <p>{@code productId}（snowflake 主键）经 ruoyi JacksonConfig 自动序列化为 string。</p>
 *
 * @author djs
 * @since D12X-MP-BURN-IA-001
 */
@Data
public class BurnProductTypeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品主键（snowflake，序列化为 string；提交时作为 productTypeItems.productId）。
     */
    private Long productId;

    /**
     * 产品业务码（如 PROD-WHITE-BAR-WHOLE → 实际为 PROD-WHITE-BAR-01 整只）。
     */
    private String productCode;

    /**
     * 产品名称（白条·整只 / 白条·半只 / 白条·猪头 / 白条·猪蹄）。
     */
    private String productName;

}
