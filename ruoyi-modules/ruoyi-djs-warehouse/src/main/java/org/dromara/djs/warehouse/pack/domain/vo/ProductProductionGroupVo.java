package org.dromara.djs.warehouse.pack.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 发货产品生产记录 — 产品维度聚合行 VO。
 *
 * <p>按「生产日期 + 产品(product_id)」分组聚合：同一日期同一产品的 N 件生产记录合并成一行。
 * 主列表只展示概览（生产日期 / 产品名称 / 产品品类 / 生产量 / 件数），逐件明细经「查看」
 * 下钻 {@link org.dromara.djs.warehouse.pack.controller.ProductProductionController#items}
 * 子页查看。范式同 {@link org.dromara.djs.warehouse.demand.domain.vo.DemandGroupVo}。</p>
 *
 * <p>{@code productId} 序列化为 string 避免雪花 ID JS 精度截断；前端「查看」下钻携
 * {@code produceDate + productId} 锁定生产批次。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Data
public class ProductProductionGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 生产日期（DATE(produce_date)）。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date produceDate;

    /** 产品 ID（string 输出避雪花精度）。 */
    private Long productId;

    /** 产品名称（组内取任一，冗余字段同组通常一致）。 */
    private String productName;

    /** 单位（组内取任一）。 */
    private String productUnit;

    /** 规格（组内取任一）。 */
    private String productSpec;

    /** 产品品类（字典 {@code djs_belong_type}；JOIN product_info 取 belong_type，前端 dict-tag 渲染）。 */
    private String belongType;

    /** 产品类型（字典 {@code djs_product_type}：1=自产/2=外购/3=礼盒；保留兼容，主列表不展示）。 */
    private Integer productType;

    /** 生产量（组内 product_weight SUM；前端 Math.round 取整展示，后端保留小数不丢精度）。 */
    private BigDecimal produceQty;

    /** 件数（组内行数 COUNT(*)）。 */
    private Integer itemCount;
}
