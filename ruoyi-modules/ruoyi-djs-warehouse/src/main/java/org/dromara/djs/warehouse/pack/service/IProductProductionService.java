package org.dromara.djs.warehouse.pack.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.pack.domain.bo.CeleryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.DryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.GiftPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.VegPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.WhiteBarOutBo;
import org.dromara.djs.warehouse.pack.domain.query.ProductProductionQuery;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionVo;
import org.dromara.djs.warehouse.pack.domain.vo.StoreDemandCopiesVo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;

import java.util.List;

/**
 * 发货产品生产记录 Service（WMS-PACK-001）。
 *
 * <p>4 业态打包入口（蔬菜 / 礼盒 / 干货 / 芹菜）+ admin 只读查询。
 * 礼盒打包按 Kevin override 决策 b 走 D8 落的 {@code t_warehouse_gift_box} 关联表，
 * 不暴露"组件清单"参数，service 自动按 box_product_id 查 gift_box 拿明细。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
public interface IProductProductionService {

    /**
     * 蔬菜打包提交 → 返新建 production.id。
     */
    Long submitVegPack(VegPackBo bo);

    /**
     * 礼盒打包提交（按 D8 gift_box 关联表组件清单）→ 返新建 production.id。
     */
    Long submitGiftPack(GiftPackBo bo);

    /**
     * 干货打包提交 → 返新建 production.id。
     */
    Long submitDryPack(DryPackBo bo);

    /**
     * 芹菜按重量打包提交 → 返新建 production.id。
     */
    Long submitCeleryPack(CeleryPackBo bo);

    /**
     * 白条/猪肉出库(发货领用)：inhouse → product_production（前缀 B/Z、绑门店），
     * 解锁白条/猪肉发货链（WMS-WHITEBAR-SHIP-001）。返新建 production.id。
     */
    Long submitWhiteBarOut(WhiteBarOutBo bo);

    /**
     * admin 分页查询（按 produce_no / product_id / product_type / pack_status / 时间区间）。
     */
    TableDataInfo<ProductProductionVo> queryPageList(ProductProductionQuery query, PageQuery pageQuery);

    /**
     * admin 列表（同 query 不分页，导出用）。
     */
    List<ProductProductionVo> queryList(ProductProductionQuery query);

    /**
     * admin 产品列表下钻（按"生产日期 + 产品"锁定一个生产批次，列出该批次逐件产品）。
     *
     * <p>主列表「查看」→ 子页「产品列表」：逐件返回 produce_no（生产编号）/ product_sort（产品序号）/
     * product_weight（产品重量）/ store_name（所属门店）/ trace_code（追溯码），按 product_sort 升序。
     * query 必带 produceDate + productId；可选 productSort（序号筛选）/ storeId（所属门店筛选）。</p>
     */
    TableDataInfo<ProductProductionVo> queryItemPageList(ProductProductionQuery query, PageQuery pageQuery);

    /**
     * admin 详情。
     */
    ProductProductionVo queryById(Long id);

    /**
     * mp 端 - 蔬菜可打包来源（{@code product_inhouse where plot_id IS NOT NULL AND del_flag='0'}）。
     */
    List<ProductInhouse> listSourceForVeg();

    /**
     * mp 端 - 干货可打包来源（来源 inhouse 按 product_workshop 过滤；V1 简化返全部活动 inhouse）。
     */
    List<ProductInhouse> listSourceForDry();

    /**
     * mp 端 - 芹菜可打包来源（蔬菜类 inhouse）。
     */
    List<ProductInhouse> listSourceForCelery();

    /**
     * 白条/猪肉出库可选来源 inhouse（belong_type ∈ {white_bar, pork} 的活动过程产品）。
     */
    List<ProductInhouse> listSourceForWhiteBar();

    /**
     * 某产品各门店未发货需求份数（打包录入「门店(N份)」标签条）。
     *
     * <p>按 productId 在需求管理表聚合各门店 {@code demand_quantity - shipped_count > 0} 的剩余量。
     * 纯展示/选择参考——打包提交仍单店语义，不做多店拆分。</p>
     *
     * @param productId 产品 FK（{@code t_warehouse_product_info.id}）
     * @return 各门店未发货份数（无则空 List）
     */
    List<StoreDemandCopiesVo> listStoreDemandCopies(Long productId);

}
