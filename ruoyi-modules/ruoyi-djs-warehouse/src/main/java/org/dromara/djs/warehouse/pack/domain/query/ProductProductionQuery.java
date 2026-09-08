package org.dromara.djs.warehouse.pack.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

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
     * 产品品类多选（R70 产品品类下拉多选）。非空时按 IN 过滤（下推 product_info.belong_type），
     * 优先于单值 belongType。
     */
    private List<String> belongTypes;

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
     * 是否只看「已发货清点」的产出（{@code is_delivery_check = 1}）。
     *
     * <p>需求下单「产品明细」置 true：那个弹框要列的是<b>已到店的那部分</b>产出，
     * 而「到店量」正是按 {@code is_delivery_check = 1} 聚合的
     * （{@code DemandArrivedQuantityFiller#selectArrivedQuantityByDemandIds}）。</p>
     *
     * <p><b>该弹框的过滤条件到此为止</b>（D-0048）：到店量聚合只有 {@code demand_id} +
     * {@code is_delivery_check = 1} 两个条件、没有 {@code deliver_dest} 过滤，明细多加任何一道，
     * 行的抵扣量之和就对不上需求行显示的到店量（线上实证：需求 2089615514926686209 到店量 100，
     * 加 {@link #excludeGiftDeliver} 后只剩 1 行 50）。要改先改到店量那一侧，两处同步。</p>
     *
     * <p>门店损耗页同样按 demandId 下钻但<b>不</b>置本参数（默认 null = 不过滤），
     * 保持它原来的可见范围。</p>
     */
    private Boolean deliveryChecked;

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
     * 是否排除「礼盒组件」产出（{@code deliver_dest='gift'}）。
     *
     * <p><b>当前没有调用方置 true</b>（D-0048）：礼盒组件（{@code fulfillDirectDemandOnPack} 对 gift
     * 早返回不扣需求）压根不写 {@code demand_id}，按 demandId 下钻时这道过滤对 gift 是空转、
     * 实际只挡掉 {@code warehouse_out}（仓库自用出库）—— 而那类产出<b>是</b>计进到店量的，
     * 挡掉就会让明细少于到店量。保留本参数是因为 D-0048（到店量该不该排除仓库自用）尚未拍板：
     * 若甲方定为「排除」，到店量聚合与本参数要一起打开。</p>
     */
    private Boolean excludeGiftDeliver;

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
