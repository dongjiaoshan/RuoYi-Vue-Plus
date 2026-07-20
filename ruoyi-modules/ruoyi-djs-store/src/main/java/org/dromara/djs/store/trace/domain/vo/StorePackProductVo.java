package org.dromara.djs.store.trace.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店猪肉打包可选产品 VO（STORE-TRACE-PACK-PRODUCT-001）。
 *
 * <p>门店现场分割白条按产品打包：产品取数 = 生产车间「门店打包间」（{@code product_workshop=5}）下、
 * 且 {@code product_material} 命中字典 {@code djs_pork_return_product} 的猪肉产品（docx「门店猪肉打包」两步取数）。
 * 前端 PorkTracePanel 产品卡按此列表渲染（替代旧固定 5 部位字典 djs_pork_cut_product）。</p>
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
}
