package org.dromara.djs.warehouse.flow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 物资领用「待领产品卡」VO（FIX-WMS-MATISSUE-001）。
 *
 * <p>口径：以某业态（{@code belong_type}）的产品为粒度，LEFT JOIN
 * {@code t_warehouse_location_stock} 聚合当前库存；并按当前登录人 + 今日子查询出
 * 已领 / 已退 / 已损（与「今日数据卡」「最大可领 / 退 / 损」上限计算同源）。</p>
 *
 * <p>原型卡片字段：缩略图 + 名称 + 右上「库存：N 单位」+「今日已领 / 今日退回 / 今日损耗」。
 * 与 {@link PackingItemVo}（仅库存 + 盘点日期，全租户口径）区别：本 VO 多了
 * <b>当前操作人今日三量</b>（领 / 退 / 损），直接驱动卡片与点入表单的上限。</p>
 *
 * <p>跨层契约：{@code productId} / {@code locationId} 是 snowflake，前端按 string 处理。</p>
 *
 * @author djs
 * @since FIX-WMS-MATISSUE-001
 */
@Data
public class MatIssueItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品主键（snowflake；前端按 string 处理，点卡进表单时作 productId）。
     */
    private Long productId;

    /**
     * 产品业务码（如 PROD001）。
     */
    private String productCode;

    /**
     * 产品名称（卡片主标题，如「番茄」「包材名称1」）。
     */
    private String productName;

    /**
     * 单位（个 / 头 / Kg 等）。
     */
    private String productUnit;

    /**
     * 缩略图 public URL（IMG-LIB-001 4 层 resolver 回填；卡片左侧图。SQL 先取
     * {@code image_oss_id}，service 层经 resolver 转 url + 兜底默认图后回写本字段）。
     */
    private String productThumb;

    /**
     * 产品归属类型（{@code djs_belong_type}；resolver L2 兜底键，可空 → 走全局默认图）。
     * 仅供 service 层 resolver 回填用，前端不消费。
     */
    private String belongType;

    /**
     * 商品分类（字典 {@code djs_buy_class} 的 value；仅 {@code issueItemsByType}（按库位类型列外购商品）
     * 端点回填，其余按 {@code belongType} 组织的端点不回填即 null）。
     *
     * <p>mp 拿到列表后 {@code [...new Set(items.map(i => i.buyClass).filter(Boolean))]} 去重成商品分类
     * 筛选 chip/下拉；label 走 mp 字典 store（{@code djs_buy_class}）显中文。前端按 string 处理。</p>
     */
    private String buyClass;

    /**
     * 当前库存（跨库位 SUM；指定 locationId 时为该库位库存；无库存行为 0）。
     */
    private BigDecimal currentStock;

    /**
     * 默认库位 ID（该产品库存最多的库位；点卡进表单时作默认 locationId，可能为空）。
     */
    private Long defaultLocationId;

    /**
     * 当前登录人今日已领（pick_out SUM；驱动「今日已领」+ 退/损上限）。
     */
    private BigDecimal todayPicked;

    /**
     * 当前登录人今日已退（return_in SUM）。
     */
    private BigDecimal todayReturned;

    /**
     * 当前登录人今日损耗（loss SUM）。
     */
    private BigDecimal todayLoss;

    /**
     * 地块 ID（步11 偏差修复 · 决策 a：自产果蔬「按地块维度」领用专用）。
     *
     * <p>仅 {@code selectSelfVegIssueItems}（自产果蔬可领用列表）回填非空：自产果蔬库存按
     * {@code (plot_id, location)} 维度建账（无 product_id），列表项以 plotId 标识。前端蔬菜 tab
     * 点此卡进领用表单时，把 plotId 回填到 {@code MatPickBo.plotId}（而非 productId），后端走
     * plot 维度扣减 + pick_out 流水带 plot_id。常规 product 维度领用列表此字段为 null。
     * snowflake，前端按 string 处理。</p>
     */
    private Long plotId;

    /**
     * 地块编码（步11；自产果蔬列表项次标签展示，如「PLOT001」，可空）。
     *
     * <p>仅 {@code selectSelfVegIssueItems} 回填；常规 product 维度领用列表为 null。</p>
     */
    private String plotCode;

}
