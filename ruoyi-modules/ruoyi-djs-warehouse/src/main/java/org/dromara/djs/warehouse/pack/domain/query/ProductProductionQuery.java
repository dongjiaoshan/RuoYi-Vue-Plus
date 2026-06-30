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
     * 产品名称模糊匹配（聚合主列表「产品名称」搜索，LIKE %kw%）。
     */
    private String productName;

    /**
     * 生产日期精确匹配（产品列表下钻按"生产日期 + 产品"锁定一个生产批次）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date produceDate;

    /**
     * 产品序号模糊匹配（下钻子页"序号"搜索框，对 product_sort 做 CAST(... AS CHAR) LIKE %kw%）。
     */
    private String productSort;

    /**
     * 产品类型字典 {@code djs_product_type}：1=自产 / 2=外购（已废弃 3 礼盒；礼盒 = 自产 + belongType=gift_box）。
     */
    private Integer productType;

    /**
     * 产品品类字典 {@code djs_belong_type}：pork / vegetable / gift_box…
     * （聚合主列表「产品品类」筛选，经 product_info JOIN 过滤）。
     */
    private String belongType;

    /**
     * 生产日期起（聚合主列表区间筛选，截到天）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date produceDateFrom;

    /**
     * 生产日期止（聚合主列表区间筛选，截到天）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date produceDateTo;

    /**
     * 打包状态字典 {@code djs_pack_status}：pending / packed / shipped_out。
     */
    private String packStatus;

    /**
     * 需求 FK → {@code t_warehouse_demand_manage.id}（门店损耗页按需求过滤逐件，契约 a）。
     */
    private Long demandId;

    /**
     * 是否损坏字典 {@code djs_yes_no}：1=是 / 0=否（逐件子页「是否损坏」筛选，契约 a；空=全部）。
     */
    private Integer isDamaged;

    /**
     * 是否存在损坏（聚合主列表 row50 筛选，作用于组维度）：1=该组 {@code SUM(is_damaged)>0} / 0==0；空=全部。
     */
    private Integer hasDamage;

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
