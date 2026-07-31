package org.dromara.djs.store.trace.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店猪肉打包可选产品 VO（STORE-TRACE-PACK-PRODUCT-001）。
 *
 * <p>门店现场分割白条按产品打包：产品取数 = 生产车间「门店打包间」（{@code product_workshop=5}）下的
 * 生产产品（{@code product_attr=1}）且业态为猪肉（{@code belong_type='pork'}）。
 * 前端 PorkTracePanel 产品卡按此列表渲染，为空时回退部位字典 {@code djs_pork_cut_product}。</p>
 *
 * @author djs
 * @since STORE-TRACE-PACK-PRODUCT-001
 */
@Data
public class StorePackProductVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品主键（雪花）。 */
    private Long productId;

    /** 产品业务码（{@code t_warehouse_product_info.product_id}）。 */
    private String productCode;

    /** 产品名称（卡片标题 + 传给生码 cutLabel）。 */
    private String productName;

    /** 产品规格。 */
    private String productSpec;

    /** 缩略图 OSS id（用户上传，优先）。 */
    private String productThumb;

    /** 主图 OSS id（图库自动匹配，兜底）。 */
    private String imageOssId;

    /**
     * 对应原材料产品 id（{@code product_info.product_material}）。
     *
     * <p>部位字典兜底分支（无 {@code product_workshop=5} 产品）下，产品卡本身就是原材料部位，
     * 此处即该原材料产品自身的 id。未配原材料 → null。</p>
     *
     * @since admin row160
     */
    private Long materialId;

    /** 对应原材料产品名称（未配 → null）。 */
    private String materialName;

    /**
     * 门店盘点当日录入的该原材料入库量（kg；{@code t_store_daily_ledger.inbound_qty}）。
     *
     * <p>当日该门店该原材料没有盘点行 → {@code 0}（admin row160 客户口径：「门店盘点未录入则显示为 0」）。</p>
     *
     * @since admin row160
     */
    private BigDecimal materialInboundQty;

    /**
     * 该原材料当日剩余可打包重量 kg = {@link #materialInboundQty} − 当日该门店已现场打包消耗该原材料的重量合计。
     *
     * <p>下限 0（不出现负数）。前端产品卡按 g 展示，生码时以此为硬上限（admin row161）。</p>
     *
     * @since admin row160
     */
    private BigDecimal materialRemainingQty;
}
