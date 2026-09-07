package org.dromara.djs.warehouse.boardstat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 「入库明细」一行（mp 仓库统计品类卡 → 入库明细，V6-R178）。
 *
 * <p>一行 = 一条计入卡片「入库量」的入库流水，{@code qty} 就是聚合里被 SUM 的那一列，
 * 故按单位 Σ qty 等于卡片数字。</p>
 *
 * @author djs
 */
@Data
public class InboundDetailRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 入库业务日期（流水 flow_date，到时分秒）。 */
    private LocalDateTime flowDate;

    /** 产品名称。 */
    private String productName;

    /** 产品规格（档案未填 → service 兜成 "—"）。 */
    private String productSpec;

    /** 入库量（流水 change_quantity 绝对值）。 */
    private BigDecimal qty;

    /** 计量单位（产品档案 product_unit；档案未填 → service 兜成「未标单位」）。 */
    private String unit;

    /** 入库方式原始值（djs_flow_type，service 翻译后不下发，仅内部用）。 */
    private String flowType;

    /** 入库方式（djs_flow_type 字典 label）。 */
    private String inModeName;

    /** 供应商名称（无供应商 / 档案已删 → service 兜成「—」）。 */
    private String supplierName;

    /** 入库库位名称（流水未记库位 / 库位已删 → service 兜成「—」）。 */
    private String locationName;
}
