package org.dromara.djs.warehouse.pack.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.pack.domain.bo.CeleryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.DryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.GiftPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.VegPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.WhiteBarOutBo;
import org.dromara.djs.warehouse.pack.domain.query.ProductProductionQuery;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionGroupVo;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionVo;
import org.dromara.djs.warehouse.pack.domain.vo.StoreDemandCopiesVo;
import org.dromara.djs.warehouse.pack.domain.vo.VegDailyLossVo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
     * admin 产品维度聚合分页查询（主列表「产品生产」概览）。
     *
     * <p>按 {@code (product_id, DATE(produce_date))} 分组：生产日期 / 产品名称 / 产品品类(belong_type) /
     * 生产量(SUM product_weight) / 件数(COUNT)。逐件明细经「查看」下钻
     * {@link #queryItemPageList} 子页。范式同 DemandManage 聚合（全量查 + 内存分页）。</p>
     */
    TableDataInfo<ProductProductionGroupVo> queryGroupPageList(ProductProductionQuery query, PageQuery pageQuery);

    /**
     * admin 逐件分页查询（按 produce_no / product_id / product_type / pack_status / 时间区间）。
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
     * 肉品打包可选来源（{@code belong_type='pork'} 的活动 inhouse，ear_no = 来源猪只耳号）。
     *
     * <p>仅返猪肉过程产品，排除白条/蔬菜，供肉品打包页「耳号去重选择条」用。</p>
     */
    List<ProductInhouse> listSourceForMeat();

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

    /**
     * 批量查目标成品的「原材料实时库存」（打包录入卡片库存口径统一，取数逻辑 doc#13）。
     *
     * <p>对每个目标成品取其 {@code product_material}（自引用 FK → 原材料产品 id），调
     * {@link #sumProductStock 原材料库位库存合计}（{@code location_stock} 求和）。<b>与打包校验/扣减用的库存口径完全一致</b>，
     * 修复「操作员看到的库存 ≠ 系统校验/扣减的库存」（卡片展示原先取来源 inhouse 汇总，与扣减口径分裂）。</p>
     *
     * <p>返回 Map：key = 成品雪花 id 字符串（避免 JS Number 精度丢失），value = 原材料当前库存合计。
     * <b>未配 {@code product_material} 的成品不进 Map</b>（前端展示 '—'，不参与校验）；
     * 产品库为空（运营未录数据）时返空 Map，优雅降级不报错。</p>
     *
     * @param productIds 目标成品 id 列表（{@code t_warehouse_product_info.id}）
     * @return 成品 id 字符串 → 原材料库存合计（无配料/空入参 → 空 Map）
     */
    Map<String, BigDecimal> listMaterialStock(List<Long> productIds);

    /**
     * 果蔬日损耗 compute-on-read 查询（V4，果疏产品全流程处理.docx）。
     *
     * <p>按自然日聚合果蔬（{@code belong_type='vegetable'}）流水，
     * 计算 {@code 日损耗 = 领用 − 打包 − 退回 − 饲喂}：
     * 领用={@code pick_out}（物资领用出库）/ 打包={@code pack_in}（果蔬打包入库）/
     * 退回={@code return_in}。饲喂项物资领用 V1 无果蔬饲喂操作恒 0（待客户确认是否新增来源）。
     * 不建汇总表，纯只读聚合。数据残缺（对应 flow_type 当日无流水）时该分量为 0，优雅降级不阻塞。</p>
     *
     * @param statDate 统计自然日（{@code null}=当天）
     * @return 果蔬日损耗（永不 null）
     */
    VegDailyLossVo queryVegDailyLoss(Date statDate);

}
