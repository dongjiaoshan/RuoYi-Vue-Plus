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

    /**
     * 产品图 public URL（IMG-LIB-001 4 层 resolver 回填，L2 兜底 white_bar 默认图）。
     */
    private String imageUrl;

    /**
     * 结构化产品类别（FIX-WMS-MP-BURN-001 录入约束用）：
     * whole=整只 / half=半只 / head=猪头 / trotter=猪蹄。
     *
     * <p>按 {@code product_id} 业务码后缀映射（PROD-WHITE-BAR-01/02/03/04），让 mp 端互斥/限次约束
     * （整只半只互斥各 1 次、猪头 1 次、猪蹄不限）+ 「猪头猪蹄不显白条编码」基于结构化枚举判断，
     * 不靠 productName 字符串脆判。未识别码返回 {@code null}。</p>
     */
    private String productType;

    /**
     * 该产品在当前白条已入库份数（row171）。
     *
     * <p>仅当 {@code productTypes} 端点带 {@code barInfoId} 入参时回填 = 该白条 {@code product_inhouse}
     * 中该 productId 的行数（半只两扇分两次录 → 2；未录 → 0）。用于 mp 卡片「已入库 x/2」计数在
     * 页面重进 / 热重载后仍准确（不再仅靠前端 session Map，重进 singing 白条时 session 为空会丢计数）。
     * 不带 barInfoId 时恒 0。</p>
     */
    private Integer recordedCount;

}
