package org.dromara.djs.warehouse.pack.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 发货产品生产记录查询（WMS-PACK-001）。
 *
 * <p>admin 列表筛选维度：produce_no / product_id / product_type / pack_status / 时间区间。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Data
public class ProductProductionQuery {

    /**
     * 生产编号精确匹配。
     */
    private String produceNo;

    /**
     * 产品 ID 精确匹配。
     */
    private Long productId;

    /**
     * 产品类型字典 {@code djs_product_type}：1=自产 / 2=外购 / 3=礼盒。
     */
    private Integer productType;

    /**
     * 打包状态字典 {@code djs_pack_status}：pending / packed / shipped_out。
     */
    private String packStatus;

    /**
     * 来源耳号精确匹配（猪肉）。
     */
    private String earNo;

    /**
     * 来源地块（蔬菜）。
     */
    private Long plotId;

    /**
     * 需求门店。
     */
    private Long storeId;

    /**
     * 生产时间起。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date produceTimeFrom;

    /**
     * 生产时间止。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date produceTimeTo;

}
