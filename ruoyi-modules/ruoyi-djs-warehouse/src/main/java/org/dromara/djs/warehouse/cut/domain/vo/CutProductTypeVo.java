package org.dromara.djs.warehouse.cut.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分割车间产品类型 VO（分割录入按产品动态加载，对标燎毛 BurnProductTypeVo）。
 *
 * <p>分割出库称重需按分割成品 SKU（精瘦肉 / 部位肉 / 骨类 / 猪皮 / 碎料）分别录入。
 * 本 VO = {@code t_warehouse_product_info} 中 {@code product_workshop=2} 分割车间 +
 * {@code belong_type='pork'} 且 {@code product_id LIKE 'PROD-PIG-%'} 的标准 5 行
 * （排除 D0001 / DEMO / 测试数据）。mp 出库录入页每类型一卡，录入该类型重量 →
 * 提交时 partItems.productId 指向所选类型。</p>
 *
 * <p>{@code productId}（snowflake 主键）经 ruoyi JacksonConfig 自动序列化为 string。</p>
 *
 * @author djs
 */
@Data
public class CutProductTypeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品主键（snowflake，序列化为 string；提交时作为 partItems.productId）。
     */
    private Long productId;

    /**
     * 产品业务码（PROD-PIG-LEAN-01 / PROD-PIG-PART-01 / ...）。
     */
    private String productCode;

    /**
     * 产品名称（猪肉·精瘦肉 / 猪肉·部位肉 / ...）。
     */
    private String productName;

    /**
     * 产品单位（默认 kg）。
     */
    private String productUnit;

    /**
     * 产品图 public URL（IMG-LIB-001 4 层 resolver 回填，L2 兜底 pork 默认图）。
     */
    private String imageUrl;

}
