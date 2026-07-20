package org.dromara.djs.warehouse.flow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

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
     * 领用篮子 ID（= {@code location_stock.id}，snowflake；前端按 string 处理）。
     *
     * <p>admin「生产物资领用」行粒度端点 {@code selectAdminMatIssueRows} 回填：一行 = 一个
     * {@code location_stock} 篮子（同产品在多库位 / 多耳号 / 多白条 → 各出一行），本字段是该行篮子的
     * 唯一主键。猪肉分割原料按【耳号 + 白条号】建篮（同一产品同一库位可有多条不同耳号 / 白条号的篮），
     * 故 admin 领用 / 退回 / 损耗必须回传本 {@code batchId} 走 service {@code pickByBatch} /
     * {@code returnByBatch} / {@code lossByBatch}（按该篮 id 行锁精确扣减 + 写该篮 {@code ear_no} /
     * {@code white_bar_no} 源标签），而非按 {@code (productId, locationId)} FIFO 跨耳号扣减——否则会出现
     * 「点耳号 A 的行却扣到耳号 B 篮」的串扣（row126）。</p>
     *
     * <p>mp 端 product 聚合端点（{@code selectMatIssueItems} 等）不回填即 null，走原 product 维度路径。</p>
     */
    private Long batchId;

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
     * 当前登录人今日最近一次领用时间（{@code MAX(flow_date)} WHERE pick_out + 今天 + 本产品 + 本人）。
     *
     * <p>用于列表排序：「有今日领用的数据优先 + 按领用时间降序」。无今日领用 → null（排在已领项之后，
     * 再按库存升序 + 产品名兜底）。前端 {@code mergeIssueItems} 跨库合并时取一次、不累加（同产品跨库同值）。</p>
     */
    private Date lastPickTime;

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

    /**
     * 库位名称（admin「生产物资领用」WMS-MATPICK-ADMIN-001 回填；mp 端点不回填即 null）。
     *
     * <p>admin 列表以 {@code location_stock} 行粒度展示（产品编码 / 库位 / 产品名 / 当前库存 / 单位 / 耳号 /
     * 地块编号 / 今日四量），故需库位名列。{@code selectAdminMatIssueRows} LEFT JOIN
     * {@code t_warehouse_location_info} 取 {@code location_name} 回填。</p>
     */
    private String locationName;

    /**
     * 耳号（admin「生产物资领用」WMS-MATPICK-ADMIN-001 回填；猪肉分割原料按耳号建账时非空，其余 null）。
     *
     * <p>additive 字段：mp 既有端点（{@code selectMatIssueItems} 等按 product 聚合）不回填本字段，
     * 仅 admin 行粒度列表 {@code selectAdminMatIssueRows} 回填。前端按 string 处理（无截断风险，业务码）。</p>
     */
    private String earNo;

    /**
     * 白条号（admin「生产物资领用」WMS-MATPICK-ADMIN-001 回填；猪肉分割白条建账时非空，其余 null）。
     *
     * <p>additive 字段：mp 既有端点（{@code selectMatIssueItems} 等按 product 聚合）不回填本字段，仅 admin
     * 行粒度列表 {@code selectAdminMatIssueRows} 回填。与 {@link #earNo} 一起构成猪肉篮的复合业务标识
     * （同一产品同一库位可有多条不同耳号 / 白条号的篮），列表展示 barID + 领用走 {@link #batchId} 精确扣该篮。
     * 前端按 string 处理（业务码无截断风险）。</p>
     */
    private String whiteBarNo;

    /**
     * 当前登录人（admin 全人）今日饲喂（feed_out SUM；行55 果蔬产品「饲料饲喂」操作驱动）。
     *
     * <p>additive 字段：mp 既有端点不回填（默认 null / 0）；admin 行粒度列表
     * {@code selectAdminMatIssueRows} 子查询今日 {@code stock_flow flow_type='feed_out'} 当日 SUM
     * （不按 operator 过滤，admin 看全部人）。果蔬业态才有饲喂操作，其余业态恒 0。
     * mp 卡片端点（{@code selectMatIssueItems} / {@code selectMatIssueItemsByType}）也回填本字段，
     * 驱动「今日已饲喂」展示 + 退回上限扣减（行3.3：已饲喂的不许退回）。</p>
     */
    private BigDecimal todayFeed;

    /**
     * 今日生产消耗（行3.3：已生产消耗的重量不许退回）。
     *
     * <p>可打包食品原料（vegetable/egg/dry_good/other）领用即进 {@code product_inhouse} 待打包，
     * 生产打包 {@code consumeInhouse} 扣减 inhouse 但<b>不写 stock_flow</b>，故无独立 flow_type 可 SUM。
     * 本字段 = {@code 今日已领 − 已退 − 已损 − 已饲 − 今日 inhouse 剩余}（派生量，仅可打包食品有意义、展示用）；
     * 非可打包物资无生产消耗概念，恒 0。前端「今日已生产」展示用，退回上限以 {@link #remainReturnable} 为准。</p>
     */
    private BigDecimal todayProduction;

    /**
     * 今日剩余可退量（行3.3 退回上限权威值）= 领用 − 退回 − 损耗 − 饲喂 − 生产消耗。
     *
     * <p>口径与 {@code MatFlowServiceImpl.ensureTodayCapacity} 写侧额度同源，前端退回 / 损耗上限直接用本值：</p>
     * <ul>
     *   <li><b>可打包食品原料</b>（vegetable/egg/dry_good/other）：= 今日 {@code product_inhouse} 剩余
     *       （{@code sumTodayRemaining}）—— 领用产 inhouse，退回 / 损耗 / 饲喂 / 生产打包都减 inhouse，
     *       天然净掉一切消耗（最准，无需逐项相减）。</li>
     *   <li><b>非可打包物资</b>（package/feed/seed/medicine）：= 今领 − 已退 − 已损 − 已饲。</li>
     * </ul>
     *
     * <p>mp 卡片端点回填；admin 行粒度端点 {@code selectAdminMatIssueRows} 不回填即 null。</p>
     */
    private BigDecimal remainReturnable;

}
