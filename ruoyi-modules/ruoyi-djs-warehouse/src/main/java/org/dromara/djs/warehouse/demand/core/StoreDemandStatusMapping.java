package org.dromara.djs.warehouse.demand.core;

import org.dromara.common.core.exception.ServiceException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 仓库 7 态 → 门店视角 5 态（字典 {@code djs_store_demand_status}）的<b>唯一口径</b>。
 *
 * <p>两个方向必须永远一致，所以放同一个类里：</p>
 * <ul>
 *   <li>{@link #derive(String, boolean)} —— 读出来的行反推门店态（列表 / 详情回填）</li>
 *   <li>{@link #sqlPredicate(String)} —— 按门店态<b>筛选</b>时下推到 SQL 的 WHERE 片段</li>
 * </ul>
 *
 * <p>映射表（与 doc 门店端 0613-04 一致）：</p>
 * <table>
 *   <tr><th>门店态</th><th>条件</th></tr>
 *   <tr><td>SUBMITTED 待确认</td><td>{@code demand_status='SUBMITTED'}</td></tr>
 *   <tr><td>CONFIRMED 已确认</td><td>{@code demand_status='CONFIRMED'} 且 {@code received_time IS NULL}</td></tr>
 *   <tr><td>SHIPPED 已发货</td><td>{@code demand_status IN ('PARTIAL_SHIPPED','COMPLETED')} 且 {@code received_time IS NULL}</td></tr>
 *   <tr><td>ARRIVED 确认到店</td><td>{@code demand_status IN ('CONFIRMED','PARTIAL_SHIPPED','COMPLETED')} 且 {@code received_time IS NOT NULL}</td></tr>
 *   <tr><td>DELETED 已删除</td><td>{@code demand_status IN ('DELETED','CANCELLED')}</td></tr>
 * </table>
 *
 * <p>门店端<b>永不返回</b> DELETED 行，故 {@link #sqlPredicate(String)} 对 DELETED 直接拒绝——
 * 允许它会与「排除已删除」的列表口径自相矛盾，宁可报错也不给两套语义。</p>
 *
 * @author djs
 * @since STORE-MP-BOARD-001
 */
public final class StoreDemandStatusMapping {

    /** 门店态：待确认。 */
    public static final String SUBMITTED = "SUBMITTED";
    /** 门店态：已确认（未收货）。 */
    public static final String CONFIRMED = "CONFIRMED";
    /** 门店态：已发货（未收货）。 */
    public static final String SHIPPED = "SHIPPED";
    /** 门店态：确认到店。 */
    public static final String ARRIVED = "ARRIVED";
    /** 门店态：已删除 / 已取消（门店端不区分）。 */
    public static final String DELETED = "DELETED";

    /**
     * 门店态 → 中文（字典 {@code djs_store_demand_status} 的 label，与 admin 展示逐字一致）。
     *
     * <p>给<b>面向店员的报错文案</b>用。mp 全站中文（CLAUDE.md §6 强约束 9），
     * 把 {@code SHIPPED} / {@code ARRIVED} 这种枚举码直接甩进 toast 是店员看不懂的。
     * 认不出的值原样返回（不吞，便于排查脏数据）。</p>
     */
    public static String labelOf(String storeStatus) {
        if (storeStatus == null) {
            return "";
        }
        return switch (storeStatus) {
            case SUBMITTED -> "待确认";
            case CONFIRMED -> "已确认";
            case SHIPPED -> "已发货";
            case ARRIVED -> "已到店";
            case DELETED -> "已删除";
            default -> storeStatus;
        };
    }

    /**
     * 门店端列表/详情统一排除的仓库态（= 门店态 DELETED）。
     * SQL 用 {@code demand_status NOT IN (...)}；软删行另由 {@code del_flag='0'} 排除。
     */
    public static final String EXCLUDED_STATUS_SQL = "('DELETED','CANCELLED')";

    private StoreDemandStatusMapping() {
    }

    /**
     * 仓库态 + 是否已收货 → 门店视角态码。
     *
     * @param demandStatus 仓库 {@code demand_status}
     * @param received     是否已门店收货（{@code received_time != null}）
     * @return 门店视角状态码；{@code demandStatus} 为 null 返 null；未知态（如 DRAFT）回退原值
     */
    public static String derive(String demandStatus, boolean received) {
        if (demandStatus == null) {
            return null;
        }
        return switch (demandStatus) {
            case "SUBMITTED" -> SUBMITTED;
            case "CONFIRMED" -> received ? ARRIVED : CONFIRMED;
            case "PARTIAL_SHIPPED", "COMPLETED" -> received ? ARRIVED : SHIPPED;
            case "DELETED", "CANCELLED" -> DELETED;
            // DRAFT 等门店端不可见态：回退原值（门店列表不展示这些行）
            default -> demandStatus;
        };
    }

    /**
     * 单个门店态 → SQL WHERE 片段（已自带外层括号，可直接 AND / OR 拼接）。
     *
     * <p>片段里只出现表自身列名（{@code demand_status} / {@code received_time}），不带表别名——
     * 调用方若用了别名需自行保证单表查询或列名不歧义。返回值是<b>常量字符串</b>，
     * 不拼接任何用户输入，无注入面。</p>
     *
     * @param storeStatus 门店态码（SUBMITTED / CONFIRMED / SHIPPED / ARRIVED）
     * @return SQL 片段
     * @throws ServiceException 空值 / 未知态 / DELETED（门店端不提供已删除查询）
     */
    public static String sqlPredicate(String storeStatus) {
        if (storeStatus == null || storeStatus.isBlank()) {
            throw new ServiceException("门店需求状态不能为空", 400);
        }
        return switch (storeStatus.trim().toUpperCase()) {
            case SUBMITTED -> "(demand_status = 'SUBMITTED')";
            case CONFIRMED -> "(demand_status = 'CONFIRMED' AND received_time IS NULL)";
            case SHIPPED -> "(demand_status IN ('PARTIAL_SHIPPED','COMPLETED') AND received_time IS NULL)";
            case ARRIVED -> "(demand_status IN ('CONFIRMED','PARTIAL_SHIPPED','COMPLETED') AND received_time IS NOT NULL)";
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
