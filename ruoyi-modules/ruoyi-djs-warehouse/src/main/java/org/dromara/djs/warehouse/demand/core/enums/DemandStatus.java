package org.dromara.djs.warehouse.demand.core.enums;

import lombok.Getter;

/**
 * 需求单生命周期 7 态（WMS-DEMAND-001 业务心脏）。
 *
 * <p>4 业态（white_bar / vegetable / gift_box / other）共享同一套状态机；业态差异在表单字段 +
 * 排产去向（service 层 confirm 时按 product_type 走业务前置校验），不在状态机本身。</p>
 *
 * <p>V1 用手写 enum + Map 查表；V2 客户提加经理审批 / 跨部门会签时切 Flowable。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Getter
public enum DemandStatus {

    /** 草稿 — 初始态，仅创建者可见可改可删。 */
    DRAFT("草稿", false),

    /** 已提交 — 等待仓库确认；可取消 / 编辑（仅 remark 类字段）。 */
    SUBMITTED("已提交", false),

    /** 已确认 — 用户侧最终态（无排产环节）；可取消；发货链路自动推进部分发货 / 完成。 */
    CONFIRMED("已确认", false),

    /** 排产中 — 已废弃态（新流程不再产生；仅存量数据兼容，发货链路仍可推进至部分发货 / 完成）。 */
    IN_PRODUCTION("排产中", false),

    /** 部分发货 — 已发部分；可继续累计 shipped_count / 完成。 */
    PARTIAL_SHIPPED("部分发货", false),

    /**
     * 已完成 — 终态。两种进入方式：
     * <ul>
     *   <li>正常发满：{@code shipped_count == demand_quantity}；</li>
     *   <li>缺量发车关闭（V6-R160）：门店当轮货没备齐、工人确认「仍要发车」后，
     *       剩下没发满的需求也推到本态，{@code shipped_count} 可以小于 {@code demand_quantity}。
     *       甲方管这叫「失效」——意思是不必再为它生产，打包台的
     *       {@code selectUncompletedDemands} 不认本态，自然就不再捞它。</li>
     * </ul>
     * 所以「本态 且 shipped_count &lt; demand_quantity」= 缺量关闭，判定式从已有列直接推出，不另立字段。
     */
    COMPLETED("已完成", true),

    /** 已取消 — 终态，DRAFT/SUBMITTED/CONFIRMED 时可取消。 */
    CANCELLED("已取消", true),

    /** 已删除 — 终态，删除（DRAFT/SUBMITTED/CANCELLED）时置位；行同时软删（del_flag），留痕用。 */
    DELETED("已删除", true);

    private final String label;
    private final boolean terminal;

    DemandStatus(String label, boolean terminal) {
        this.label = label;
        this.terminal = terminal;
    }

    /**
     * 安全解析：把 DB string 转为枚举；未知字面量返 null（service 层判 null 抛
     * {@code demand.state.invalid} 业务异常，比让 {@code IllegalArgumentException}
     * 渗到全局 handler 更友好）。
     */
    public static DemandStatus fromCodeSafe(String code) {
        if (code == null) {
            return null;
        }
        for (DemandStatus s : values()) {
            if (s.name().equals(code)) {
                return s;
            }
        }
        return null;
    }
}
