package org.dromara.djs.warehouse.demand.core;

import org.dromara.common.core.exception.ServiceException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 仓库 7 态 → 门店视角 6 态（字典 {@code djs_store_demand_status}）的<b>唯一口径</b>。
 *
 * <p>三条链路必须永远一致，所以放同一个类里：</p>
 * <ul>
 *   <li>{@link #derive(String, boolean, BigDecimal, BigDecimal)} —— 读出来的行反推门店态（列表 / 详情回填）</li>
 *   <li>{@link #sqlPredicate(String)} —— 按门店态<b>筛选</b>时下推到 SQL 的 WHERE 片段</li>
 *   <li>{@link #ARRIVED_QTY_SUBQUERY_DM} —— mp 门店<b>日卡</b>三桶（
 *       {@code DemandManageMapper#selectStoreDemandDayPage} 的 arrived/shipped/confirmed CASE）
 *       复用同一份到店量子查询。V6-R197 漏了这条，于是日卡说「已发货」、点进按天明细每行却是「已确认」，
 *       独立 QA 在 store C / 2026-09-09 复现；契约由 {@code StoreDemandDayBucketSqlContractTest} 钉死。</li>
 * </ul>
 *
 * <p>映射表（{@code arrived} = 到店量，{@code demand} = 需求量）：</p>
 * <table>
 *   <tr><th>门店态</th><th>条件</th></tr>
 *   <tr><td>SUBMITTED 待确认</td><td>{@code demand_status='SUBMITTED'}</td></tr>
 *   <tr><td>CONFIRMED 已确认</td>
 *       <td>{@code demand_status='CONFIRMED'} 且未收货；<b>或</b>已发货态但 {@code arrived <= 0}</td></tr>
 *   <tr><td>PARTIAL_ARRIVED 部分到店</td><td>已发货态且未收货且 {@code 0 < arrived < demand}</td></tr>
 *   <tr><td>SHIPPED 已发货</td><td>已发货态且未收货且 {@code arrived >= demand}</td></tr>
 *   <tr><td>ARRIVED 确认到店</td><td>任意已确认及之后的态 + {@code received_time IS NOT NULL}</td></tr>
 *   <tr><td>DELETED 已删除</td><td>{@code demand_status IN ('DELETED','CANCELLED')}</td></tr>
 * </table>
 *
 * <p><b>为什么已发货态还要看到店量（V6-R197/R198，甲方 2026-09-07）</b>：缺量出车时需求被
 * {@code forceCloseUnmetDemands} 推到 COMPLETED，但那一车上一件该产品都没有。此前门店看到的是
 * 「已发货 / 确认到店」，与「东西根本没来」直接矛盾。甲方原话「到店量等于 0 时，状态还是【已确认】状态」，
 * 以及「0 &lt; 到店量 &lt; 需求量 → 部分到店」。<b>本类只改派生显示态，不动仓库状态机
 * {@code DemandStatus} 与任何落库列</b>。</p>
 *
 * <p><b>「已删除」在两端的可见性不一样，别再按一句话理解</b>：</p>
 * <ul>
 *   <li><b>mp applet 链路</b>（日卡 / 按天明细）用 {@link #EXCLUDED_STATUSES} 在查询层就把
 *       {@code DELETED / CANCELLED / DRAFT} 挡掉，店员看不到已删除行；</li>
 *   <li><b>admin 门店管理「需求下单」列表</b>（{@code /djs/store/demand/list}）<b>有意显示全部行</b>，
 *       含已删除（甲方 row198 截图里就有「已删除」行），状态按字典 {@code djs_store_demand_status} 渲染。</li>
 * </ul>
 * <p>{@link #sqlPredicate(String)} 对 DELETED 直接拒绝，只是因为它服务的是 <b>mp 那条筛选链路</b> ——
 * 在一个「查询层已经排除已删除」的列表上再提供「只看已删除」的筛选项是自相矛盾的。
 * admin 列表不走这套筛选（它按仓库态 / 门店态多选走 {@code storeDemandStatuses}，不提供已删除选项）。</p>
 *
 * @author djs
 * @since STORE-MP-BOARD-001
 */
public final class StoreDemandStatusMapping {

    /** 门店态：待确认。 */
    public static final String SUBMITTED = "SUBMITTED";
    /** 门店态：已确认（未收货，且一件都还没到店）。 */
    public static final String CONFIRMED = "CONFIRMED";
    /** 门店态：部分到店（未收货，到店量 &gt; 0 但不足需求量；V6-R197）。 */
    public static final String PARTIAL_ARRIVED = "PARTIAL_ARRIVED";
    /** 门店态：已发货（未收货，到店量已满需求量）。 */
    public static final String SHIPPED = "SHIPPED";
    /** 门店态：确认到店。 */
    public static final String ARRIVED = "ARRIVED";
    /** 门店态：已删除 / 已取消（门店端不区分）。 */
    public static final String DELETED = "DELETED";

    /**
     * <b>mp applet 链路</b>统一排除的仓库态（= 门店态 DELETED，外加从未提交的 DRAFT）。
     * SQL 用 {@code demand_status NOT IN (...)}；软删行另由 {@code del_flag='0'} 排除。
     *
     * <p>只在 mp（日卡 / 按天明细 / 门店经营统计）用；<b>admin 门店需求列表不用它</b>，
     * 那边有意把已删除行也列出来（见类注释）。</p>
     */
    public static final String EXCLUDED_STATUS_SQL = "('DELETED','CANCELLED','DRAFT')";

    /** 同上，给 MyBatis-Plus wrapper 的 {@code notIn} 用（与 {@link #EXCLUDED_STATUS_SQL} 必须同集合）。 */
    public static final String[] EXCLUDED_STATUSES = {"DELETED", "CANCELLED", "DRAFT"};

    /**
     * 「已发货态」= 仓库侧已经出过货 / 已闭单的两个态（SQL 字面量）。
     * 到店量细分只在这两个态里发生，与 {@link #derive} 的 {@code case} 分支必须同集合。
     *
     * <p>public 是为了让日卡分桶 CASE（{@code DemandManageMapper#selectStoreDemandDayPage}）
     * 与 {@link #sqlPredicate(String)} 共用同一份状态集合，不各写一份。编译期常量，可进注解。</p>
     */
    public static final String SHIPPED_STATUS_SQL = "('PARTIAL_SHIPPED','COMPLETED')";

    /**
     * 「已确认及之后」的仓库态集合（SQL 字面量）—— 收货标记一旦置位，这几个态一律算门店态 ARRIVED。
     * 与 {@link #derive} 里 {@code received == true} 的分支同集合。
     */
    public static final String CONFIRMED_OR_LATER_STATUS_SQL =
        "('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')";

    /** 仓库侧「已确认、还没发货」的两个态（含已废弃的 IN_PRODUCTION）。 */
    public static final String CONFIRMED_STATUS_SQL = "('CONFIRMED','IN_PRODUCTION')";

    /**
     * 相关子查询：单行需求的<b>到店量</b>（SQL 侧口径，与
     * {@code ProductProductionMapper#selectArrivedQuantityByDemandIds} 的批量聚合逐条同构）。
     *
     * <p>口径 = 该需求下已被发货清点（{@code is_delivery_check=1}）的成品条数，并按业态收口
     * （成品 belong_type 必须与需求产品 belong_type 一致，挡掉挂错业态的松散绑定行）。</p>
     *
     * <p><b>拆成 PREFIX / SUFFIX 是为了让外层锚点可换而片段本身只有一份</b>：
     * 用它的两处外层写法不同 —— {@link #sqlPredicate(String)} 服务的是<b>单表无别名</b>的
     * MyBatis-Plus wrapper（锚 {@code t_warehouse_demand_manage.id}），
     * {@code DemandManageMapper#selectStoreDemandDayPage} 的日卡聚合 SQL 给主表起了别名 {@code dm}
     * （锚 {@code dm.id}）。两个常量都是<b>编译期常量</b>，所以 {@code @Select} 注解里可以直接拼
     * （{@link #ARRIVED_QTY_SUBQUERY_DM}）。手写第二份条件必然漂 —— R197 的日卡/行级矛盾就是这么来的。</p>
     *
     * <p>片段本身<b>不含</b> {@code <} / {@code >}，所以放进 MyBatis {@code <script>} 无需转义；
     * 与它比较的运算符写在各使用点（script 里要写 {@code &lt;} / {@code &gt;}）。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}，与批量聚合 SQL 同范式。</p>
     */
    public static final String ARRIVED_QTY_SUBQUERY_PREFIX =
        "(SELECT COUNT(*) FROM t_warehouse_product_production pp"
            + " JOIN t_warehouse_demand_manage sd ON sd.id = pp.demand_id"
            + " AND sd.del_flag = '0' AND sd.tenant_id = '1001'"
            + " JOIN t_warehouse_product_info spi ON spi.id = pp.product_id AND spi.del_flag = '0'"
            + " JOIN t_warehouse_product_info sdi ON sdi.id = sd.product_id AND sdi.del_flag = '0'"
            + " AND sdi.belong_type = spi.belong_type"
            + " WHERE pp.is_delivery_check = 1 AND pp.del_flag = '0' AND pp.tenant_id = '1001'"
            + " AND pp.demand_id = ";

    /** 见 {@link #ARRIVED_QTY_SUBQUERY_PREFIX}：闭合括号。 */
    public static final String ARRIVED_QTY_SUBQUERY_SUFFIX = ")";

    /**
     * 到店量子查询，锚<b>主表别名 {@code dm}</b> —— 给 {@code DemandManageMapper#selectStoreDemandDayPage}
     * 的日卡分桶 CASE 用（那条 SQL 把 {@code t_warehouse_demand_manage} 起了别名 {@code dm}）。
     *
     * <p>编译期常量，可直接出现在 {@code @Select} 注解里。日桶与 {@link #sqlPredicate(String)}
     * 共用同一片段，由 {@code StoreDemandDayBucketSqlContractTest} 钉死。</p>
     */
    public static final String ARRIVED_QTY_SUBQUERY_DM =
        ARRIVED_QTY_SUBQUERY_PREFIX + "dm.id" + ARRIVED_QTY_SUBQUERY_SUFFIX;

    /** 到店量子查询，锚单表无别名查询的 {@code t_warehouse_demand_manage.id}（{@link #sqlPredicate} 用）。 */
    private static final String ARRIVED_QTY_SUBQUERY =
        ARRIVED_QTY_SUBQUERY_PREFIX + "t_warehouse_demand_manage.id" + ARRIVED_QTY_SUBQUERY_SUFFIX;

    private StoreDemandStatusMapping() {
    }

    /**
     * 仓库态 + 是否已收货 + 到店量 + 需求量 → 门店视角态码。
     *
     * <p>null 处置（两条都是「信息不足时退回 R197 之前的行为」，绝不猜）：</p>
     * <ul>
     *   <li>{@code arrivedQty == null}（调用方没查到店量）→ 不做部分到店细分，已发货态一律 {@link #SHIPPED}；</li>
     *   <li>{@code arrivedQty != null} 但 {@code demandQty == null}（比不出「够不够」）→ 到店量 &gt; 0 时
     *       维持 {@link #SHIPPED}（到店量 ≤ 0 仍算 {@link #CONFIRMED}，这一判断不需要需求量）。</li>
     * </ul>
     *
     * @param demandStatus 仓库 {@code demand_status}
     * @param received     是否已门店收货（{@code received_time != null}）
     * @param arrivedQty   到店量（该需求下已发货清点的成品条数）；null = 未知
     * @param demandQty    需求量（{@code demand_quantity}）；null = 未知
     * @return 门店视角状态码；{@code demandStatus} 为 null 返 null；未知态（如 DRAFT）回退原值
     */
    public static String derive(String demandStatus, boolean received,
                                BigDecimal arrivedQty, BigDecimal demandQty) {
        if (demandStatus == null) {
            return null;
        }
        return switch (demandStatus) {
            case "SUBMITTED" -> SUBMITTED;
            // IN_PRODUCTION 是已废弃态（新流程不再产生），但存量行仍会走到发货/完成，
            // 对门店而言它就是「已确认、还没发货」——必须归到 CONFIRMED，不能漏成未知态：
            // 漏了会同时造成「不筛能看到、四态全选反而看不到」和状态标签直接甩英文枚举给店员。
            case "CONFIRMED", "IN_PRODUCTION" -> received ? ARRIVED : CONFIRMED;
            case "PARTIAL_SHIPPED", "COMPLETED" -> received ? ARRIVED : deriveShipped(arrivedQty, demandQty);
            case "DELETED", "CANCELLED" -> DELETED;
            // DRAFT（从未提交给仓库的草稿）等门店端不可见态：回退原值。
            // 这些行由 EXCLUDED_STATUS_SQL 在查询层就挡掉，正常不会走到这里。
            default -> demandStatus;
        };
    }

    /**
     * 与到店量无关的场景专用（判「是不是待确认」/「是不是已删除」）。
     *
     * <p>写端点（改量 / 撤回）只关心「是否 {@link #SUBMITTED}」，为一句报错文案多打一次到店量聚合
     * 不划算；这里显式声明「不知道到店量」，由 {@link #derive} 按 null 契约退回 {@link #SHIPPED}。
     * <b>不是第二套口径</b>——同一个 {@link #derive}，只是入参更少。</p>
     */
    public static String deriveIgnoringArrival(String demandStatus, boolean received) {
        return derive(demandStatus, received, null, null);
    }

    /** 已发货态（PARTIAL_SHIPPED / COMPLETED）且未收货时的三分：已确认 / 部分到店 / 已发货。 */
    private static String deriveShipped(BigDecimal arrivedQty, BigDecimal demandQty) {
        if (arrivedQty == null) {
            return SHIPPED;
        }
        if (arrivedQty.signum() <= 0) {
            // 甲方原话：「到店量等于 0 时，状态还是【已确认】状态」
            return CONFIRMED;
        }
        if (demandQty == null) {
            return SHIPPED;
        }
        return arrivedQty.compareTo(demandQty) < 0 ? PARTIAL_ARRIVED : SHIPPED;
    }

    /**
     * 单个门店态 → SQL WHERE 片段（已自带外层括号，可直接 AND / OR 拼接）。
     *
     * <p>片段里只出现表自身列名（{@code demand_status} / {@code received_time} / {@code demand_quantity}）
     * 与到店量相关子查询里的全表名限定 {@code t_warehouse_demand_manage.id}——调用方须保证是
     * <b>单表无别名</b>查询。返回值是<b>常量字符串</b>，不拼接任何用户输入，无注入面。</p>
     *
     * <p>与 {@link #derive} 逐条同构：CONFIRMED 多出「已发货态但到店量为 0」那一支，
     * PARTIAL_ARRIVED / SHIPPED 按到店量与需求量比较切开。derive 的
     * 「{@code demandQty == null} → SHIPPED」在 SQL 侧对应 {@code demand_quantity IS NULL}
     * 归 SHIPPED 分支（该列 NOT NULL，纯防御）。</p>
     *
     * @param storeStatus 门店态码（SUBMITTED / CONFIRMED / PARTIAL_ARRIVED / SHIPPED / ARRIVED）
     * @return SQL 片段
     * @throws ServiceException 空值 / 未知态 / DELETED（门店端不提供已删除查询）
     */
    public static String sqlPredicate(String storeStatus) {
        if (storeStatus == null || storeStatus.isBlank()) {
            throw new ServiceException("门店需求状态不能为空", 400);
        }
        return switch (storeStatus.trim().toUpperCase()) {
            case SUBMITTED -> "(demand_status = 'SUBMITTED')";
            // IN_PRODUCTION 与 derive() 保持同一口径（见该方法注释）：筛选与展示两边永远一致，
            // 否则会出现「不筛看得到、全选筛不到」的自相矛盾。
            case CONFIRMED -> "((demand_status IN " + CONFIRMED_STATUS_SQL + " AND received_time IS NULL)"
                + " OR (demand_status IN " + SHIPPED_STATUS_SQL + " AND received_time IS NULL"
                + " AND " + ARRIVED_QTY_SUBQUERY + " <= 0))";
            case PARTIAL_ARRIVED -> "(demand_status IN " + SHIPPED_STATUS_SQL + " AND received_time IS NULL"
                + " AND " + ARRIVED_QTY_SUBQUERY + " > 0"
                + " AND demand_quantity IS NOT NULL"
                + " AND " + ARRIVED_QTY_SUBQUERY + " < demand_quantity)";
            case SHIPPED -> "(demand_status IN " + SHIPPED_STATUS_SQL + " AND received_time IS NULL"
                + " AND " + ARRIVED_QTY_SUBQUERY + " > 0"
                + " AND (demand_quantity IS NULL OR " + ARRIVED_QTY_SUBQUERY + " >= demand_quantity))";
            case ARRIVED -> "(demand_status IN " + CONFIRMED_OR_LATER_STATUS_SQL
                + " AND received_time IS NOT NULL)";
            case DELETED -> throw new ServiceException("门店端不提供「已删除」需求查询", 400);
            default -> throw new ServiceException("不支持的门店需求状态：" + storeStatus, 400);
        };
    }

    /**
     * 多选门店态 → OR 拼接的 SQL WHERE 片段（外层再包一层括号）。
     *
     * @param storeStatuses 门店态码集合（null / 空 → 返 null，调用方不加该条件）
     * @return SQL 片段；无有效元素返 null
     * @throws ServiceException 含未知态 / DELETED
     */
    public static String sqlPredicateAny(Collection<String> storeStatuses) {
        if (storeStatuses == null || storeStatuses.isEmpty()) {
            return null;
        }
        // 去重保序：前端重复传同一态不产生重复 OR 分支
        Set<String> distinct = new LinkedHashSet<>();
        for (String s : storeStatuses) {
            if (s != null && !s.isBlank()) {
                distinct.add(s.trim().toUpperCase());
            }
        }
        if (distinct.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(" OR ", "(", ")");
        for (String s : distinct) {
            joiner.add(sqlPredicate(s));
        }
        return joiner.toString();
    }
}
