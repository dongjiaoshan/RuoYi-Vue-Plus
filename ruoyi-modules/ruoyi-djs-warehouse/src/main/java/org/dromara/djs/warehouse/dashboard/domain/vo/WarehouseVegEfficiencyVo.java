package org.dromara.djs.warehouse.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 果蔬处理效能管理 VO（mp 仓库管理看板 tab2，对齐原型 c354927f）。
 *
 * <p>消费 WMS-STAT-001 落盘表：年度 KPI 汇总当年日表 {@code t_warehouse_indicator_record}（果蔬段）
 * + 作物日表 {@code t_warehouse_cropp_record}（品类数 / TOP10）；TOP10 横条取所选月份作物日表聚合；
 * 日矩阵取近 N 日作物日表（作物行 × 指标列）。无数据时各段 0 / 空列表，不抛错。</p>
 *
 * @author djs
 * @since WMS-DASH-MP-001
 */
@Data
public class WarehouseVegEfficiencyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计年份。 */
    private Integer year;

    // ====== 年度蔬菜处理指标统计 6 KPI（原型「年度蔬菜处理指标统计」）======

    /** 收获蔬菜品类（当年作物日表去重作物数）。 */
    private Integer harvestCropKinds;
    /** 收获蔬菜总重（吨，当年作物日表采摘量之和 / 1000）。 */
    private BigDecimal harvestWeightTon;
    /** 鲜菜总重（吨，当年作物日表发往月台量之和 / 1000）。 */
    private BigDecimal freshWeightTon;
    /** 蔬菜处理率%（当年(毛菜称量−毛菜损耗)/毛菜称量×100；分母 0 → null）。 */
    private BigDecimal vegHandleRate;
    /** 路损率%（当年(发往月台−月台接收)/发往月台×100；分母 0 → null）。 */
    private BigDecimal transportLossRate;
    /** 净菜损耗率%（当年(生产损耗+录入损耗)/(生产领用−生产退回)×100；分母≤0 → null）。 */
    private BigDecimal netVegLossRate;

    // ====== 果蔬收获量 TOP10 横条（原型「果蔬收获量TOP10」）======

    /** 收获量 TOP10：作物名 + 采摘量 kg（所选月份聚合，降序）。 */
    private List<NameValue> harvestTop10 = new ArrayList<>();

    // ====== 果蔬损耗率 TOP10 横条（原型「果蔬损耗率TOP10」=毛菜间损耗率+路损率+净菜间损失率）======

    /** 损耗率 TOP10：作物名 + 综合损耗率%（所选月份聚合，降序）。 */
    private List<NameValue> lossRateTop10 = new ArrayList<>();

    // ====== 日蔬菜处理数据统计矩阵（原型「日蔬菜处理数据统计」）======

    /** 作物行（每行：作物名 + 采摘量 / 鲜菜量 / 饲喂量 / 毛菜处理率，所选月份最近一日聚合）。 */
    private List<VegDailyRow> dailyRows = new ArrayList<>();

    /** name + value 通用横条项（TOP10 用）。 */
    @Data
    public static class NameValue implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 分类名（作物名）。 */
        private String name;
        /** 值（采摘量 kg / 损耗率%；null-safe 0）。 */
        private BigDecimal value;
    }

    /** 日蔬菜处理矩阵单行（一作物一行，值已格式化字符串）。 */
    @Data
    public static class VegDailyRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 蔬菜品类（作物名）。 */
        private String cropName;
        /** 采摘量（kg，2 位小数字符串）。 */
        private String pickWeight;
        /** 鲜菜量（kg = 发往月台量，2 位小数字符串）。 */
        private String freshWeight;
        /** 饲喂量（kg，2 位小数字符串）。 */
        private String feedWeight;
        /** 毛菜处理率（%，2 位小数字符串，空值空串）。 */
        private String vegHandleRate;
    }
}
