package org.dromara.djs.warehouse.flow.service;

import org.dromara.djs.warehouse.flow.domain.bo.MatFeedBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatLossBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatPickBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatReturnBo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueBasketVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueItemVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueLocationVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatTodaySummaryVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatWhiteBarBatchVo;

import java.util.List;

/**
 * 物资领用 / 退回 / 损耗 Service（WMS-MAT-001）。
 *
 * <p>3 个核心方法（mp 工人 3 Tab 通用模板），每个均同事务跨表（stock_flow + location_stock）。
 * V1 仅"包材"实例化使用，V1.5 由白条 / 蔬菜 / 鸡蛋 / 干货 / 果蔬 / 采食 / 药品 / 种子 8 类通过
 * matType 参数复用。</p>
 *
 * @author djs
 * @since WMS-MAT-001
 */
public interface IMatFlowService {

    /**
     * 物资领用（出库）。
     *
     * @return 新增 stock_flow 主键 id
     */
    Long pick(MatPickBo bo);

    /**
     * 物资退回（入库）。
     *
     * @return 新增 stock_flow 主键 id
     */
    Long returnBack(MatReturnBo bo);

    /**
     * 物资损耗（出库）。
     *
     * @return 新增 stock_flow 主键 id
     */
    Long loss(MatLossBo bo);

    /**
     * 仓库饲料饲喂（出库，行64 来源②仓库领用饲喂 / 行55 果蔬产品「饲料饲喂」操作）。
     *
     * <p>同事务：INSERT stock_flow(flow_type='feed_out') + 扣 location_stock（不可逆消耗，账面不足打 warn 不抛）
     * + INSERT t_warehouse_feed_log(feed_type='warehouse')。</p>
     *
     * @return 新增 stock_flow 主键 id
     */
    Long feed(MatFeedBo bo);

    /**
     * 当前登录人今日已领 / 已退 / 已损汇总。
     *
     * @param matType 可选物资类型（djs_mat_type 字典 value，service 内部映射 belong_type）；null = 全部
     * @return 三项 SUM
     */
    MatTodaySummaryVo todaySummary(String matType);

    /**
     * 当前登录人今日已领 / 已退 / 已损汇总（单产品维度）。
     *
     * <p>BRD-FIX-MP-FEED-IA-001：饲料领用子页顶卡需「当前选中单产品」的今日已领 / 退回，
     * 而非全产品汇总。productId 入参 String（snowflake 防截断），内部转 Long 查询；
     * 为空 / 空白时退化为 {@link #todaySummary(String)} 全产品（或 matType 维度）。</p>
     *
     * @param matType   可选物资类型；productId 非空时本参不参与过滤
     * @param productId 产品 ID（snowflake String），可空 → 退化为 matType 维度
     * @return 三项 SUM
     */
    MatTodaySummaryVo todaySummary(String matType, String productId);

    /**
     * mp 物资领用「二级库」chip 列表（FIX-WMS-MATISSUE-001）。
     *
     * <p>某业态（belongType）下产品实际分布的库位 —— 原型每个业态 tab 下橙底库位 chip。
     * 包材业态常无库位关联 → 返空 list，前端不渲染 chip 段。</p>
     *
     * @param belongType 字典 {@code djs_belong_type}（pork / vegetable / egg / dry_good ...）
     * @return 库位 chip 列表（按库位名排序）
     */
    List<MatIssueLocationVo> issueLocations(String belongType);

    /**
     * mp 物资领用「待领产品卡」列表（FIX-WMS-MATISSUE-001）。
     *
     * <p>某业态产品 + 当前库存 + 当前登录人今日已领 / 退 / 损（驱动卡片与点入表单上限）。</p>
     *
     * @param belongType 字典 {@code djs_belong_type}
     * @param locationId 库位 ID（可空，chip 选中态过滤）
     * @return 待领产品卡列表（库存升序）
     */
    List<MatIssueItemVo> issueItems(String belongType, String locationId);

    /**
     * mp 物资领用「按库位类型列库位 chip」列表（WMS-OUTSOURCE-001：种植库 crop_loc / 养殖库 farm_loc）。
     *
     * <p>与 {@link #issueLocations(String)}（按业态组织）对称：本方法按 {@code location_type} 列出该类型
     * 下全部库位，按 location_sort 排序。前端首项「全部」+ 各库位 chip。</p>
     *
     * @param locationType 字典 {@code djs_location_type} 的 value（{@code crop_loc} / {@code farm_loc}）
     * @return 库位 chip 列表（按 location_sort 排序）；无则空 list
     */
    List<MatIssueLocationVo> issueLocationsByType(String locationType);

    /**
     * mp 物资领用「按库位类型列待领外购商品卡」列表（WMS-OUTSOURCE-001）。
     *
     * <p>与 {@link #issueItems(String, String)}（按业态组织）对称：本方法按 {@code location_type} 列出该类型
     * 库位下有库存的外购商品（{@code product_type=2 + product_status=0 + 库存>0}），VO 额外回填
     * {@code buyClass}（商品分类，前端去重成分类筛选 chip）。productThumb 走 resolver 批量回填禁 N+1。</p>
     *
     * @param locationType 字典 {@code djs_location_type} 的 value（{@code crop_loc} / {@code farm_loc}）
     * @param locationId   库位 ID（可空，chip 选中态过滤；为空则该类型全库位聚合）
     * @return 待领外购商品卡列表（库存升序）；无则空 list
     */
    List<MatIssueItemVo> issueItemsByType(String locationType, String locationId);

    /**
     * 领用前置校验：该原材料是否允许领用（mp 物资领用硬拦截）。
     *
     * <p>规则（Kevin 2026-06-21）：打包业态（果蔬/猪肉/鸡蛋/干货/其他）的原材料(attr=2) **必须**有对应成品
     * （领用 → 打包成成品，无成品则领了也打不出来）→ 无成品时前端弹「原材料没有对应的生产产品，请先创建」并**禁止领用**。
     * 包材/饲料/种子/白条等不打包成产品（领用去消耗/投喂/播种/分割）→ 不受此约束，恒允许。</p>
     * <ul>
     *   <li>{@code productId} 非空（外购/包材 tab0、按篮 batch）→ 解析成 Long 作原料 id。</li>
     *   <li>否则 {@code plotId} 非空（自产果蔬 tab1）→ plot→crop→{@code crop.related_product} 解析出果蔬原料 id。</li>
     *   <li>解析不到原料 / 非原材料(attr!=2) / 非打包业态 → 返 {@code true}（不拦，允许领用）。</li>
     *   <li>打包业态原材料 → 返 EXISTS 成品（{@code product_attr=1 且 product_material=该原料 id}）。</li>
     * </ul>
     *
     * @param productId 原材料产品 id（snowflake String，可空）
     * @param plotId    地块 id（snowflake String，可空；自产果蔬走 plot→crop→related_product 解析）
     * @return true=允许领用；false=打包业态原材料但无对应成品 → 禁止领用
     */
    boolean canIssueMaterial(String productId, String plotId);

    /**
     * mp 物资领用「自产果蔬可领用」列表（步11 偏差修复 · 决策 a；蔬菜 tab 自产果蔬数据源）。
     *
     * <p>自产果蔬库存按 {@code (plot_id, location)} 维度建账（无 product_id），常规
     * {@link #issueItems}（按 product_id JOIN）列不到。本方法以 plot 维度列出可领用项：
     * 复用 {@link MatIssueItemVo}，回填 {@code plotId} / {@code plotCode}（productId 为 null），
     * 前端点卡进领用表单时把 plotId 回填 {@code MatPickBo.plotId} 走 plot 维度领用。</p>
     *
     * @param locationId 库位 ID（可空，chip 选中态过滤；snowflake string 防截断，service 内 parse）
     * @return 自产果蔬可领用项（按作物名、地块编码排序）；无则空 list
     */
    List<MatIssueItemVo> selfVegIssueItems(String locationId);

    /**
     * mp 物资领用「白条批次卡」列表（录入形态②·白条库 / issueWhiteBarBatches）。
     *
     * <p>权威源 = {@code t_warehouse_bar_info WHERE status='in_stock'}（一行 = 一条实物白条整只），
     * <b>不走</b> location_stock（白条 SKU 无库存行）。每条在库白条 = 一张可领批次卡，VO 镜像前端
     * {@code MatWhiteBarBatchVo}：batchId / productId / locationId 全 String 防截断；productId 回填
     * 「白条·整只」canonical SKU（bar_info 无 product_id FK，由本方法给领用 BO 一个 productId）；
     * precoolDuration 按 {@code in_time} 实时计算（{@code now - in_time} → {@code {h}小时{m}分钟}，
     * 不读 acid_remove_time）；inboundTime 格式化 {@code MM-dd HH:mm}；storeName / locationId 恒 null；
     * batchStatus 恒 {@code pickable}。无在库白条 → 空 list（前端 graceful empty）。</p>
     *
     * @param belongType 字典 {@code djs_belong_type}（white_bar / pork；为对称接收，本端点不据此过滤 bar_info）
     * @param locationId 库位 ID（可空，为对称接收；bar_info 不绑库位）
     * @return 在库白条批次卡列表（按 in_time DESC 排序）；无则空 list
     */
    List<MatWhiteBarBatchVo> issueWhiteBarBatches(String belongType, String locationId);

    /**
     * mp 物资领用「按耳号源篮子」列表（「按源手选」领用，猪肉(分割)按耳号）。
     *
     * <p>对齐客户最新原型「仓库&gt;分拣发货&gt;物资领用」：用户在源篮子列表里手选某一篮领用（不再 FIFO
     * 系统选篮）。返该 pork 原料产品的 {@code location_stock} 篮子（{@code ear_no} 非空、库存&gt;0），
     * {@code batchCode = ear_no}。只返有库存的篮（用完不出现 → 「用完不可选」）。前端选中后把
     * {@code batchId} 回填 {@code MatPickBo.batchId} → service 走 {@code pickByBatch}。</p>
     *
     * @param productId  pork 原料产品 ID（snowflake string，必填，service 内 parse）
     * @param locationId 库位 ID（可空，chip 选中态过滤；snowflake string 防截断）
     * @return 耳号源篮子列表；无则空 list
     */
    List<MatIssueBasketVo> issuePorkBatches(String productId, String locationId);

    /**
     * mp 物资领用「按地块源篮子」列表（「按源手选」领用，蔬菜(自产)按地块）。
     *
     * <p>对齐客户最新原型「仓库&gt;分拣发货&gt;物资领用」：用户在源篮子列表里手选某一篮领用。返该自产果蔬
     * 原料产品的 {@code location_stock} 篮子（{@code plot_id} 非空、库存&gt;0），{@code batchCode = 地块编号}。
     * 只返有库存的篮。前端选中后把 {@code batchId} 回填 {@code MatPickBo.batchId} → service 走
     * {@code pickByBatch}。</p>
     *
     * @param productId  自产果蔬原料产品 ID（snowflake string，必填，service 内 parse）
     * @param locationId 库位 ID（可空，chip 选中态过滤；snowflake string 防截断）
     * @return 地块源篮子列表；无则空 list
     */
    List<MatIssueBasketVo> issueVegBatches(String productId, String locationId);

}
