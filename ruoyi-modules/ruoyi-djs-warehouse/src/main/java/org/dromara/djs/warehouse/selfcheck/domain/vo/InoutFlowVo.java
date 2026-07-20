package org.dromara.djs.warehouse.selfcheck.domain.vo;

import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 进出库流水卡 VO（入库记录 / 出库记录 tab）。
 *
 * <p>对应 mp 契约 {@code stockCheck.ts} InoutFlowVo。数据源 {@code t_warehouse_stock_flow}
 * 按 {@code inout_type}（IN/OT）过滤；盘点流水（check_in / check_out / check_abnormal_out）
 * 是真实入出库动作，一并按 inout_type 归入对应 tab（r195）。</p>
 *
 * <ul>
 *   <li>{@code inoutTypeLabel} 走字典 {@code djs_flow_type} 把 flow_type 转中文（注解翻译）。</li>
 *   <li>{@code operatorName} 走 {@code USER_ID_TO_NICKNAME} 注解翻译。</li>
 *   <li>{@code supplierName} LEFT JOIN {@code t_md_supplier} 回填。</li>
 * </ul>
 *
 * @author djs
 * @since SELFCHECK
 */
@Data
public class InoutFlowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流水 ID（snowflake string）。
     */
    private Long id;

    /**
     * 单号。
     */
    private String flowNo;

    /**
     * 时间 yyyy-MM-dd HH:mm:ss。
     */
    private String flowTime;

    /**
     * 产品名称。
     */
    private String productName;

    /**
     * 数量（change_quantity 绝对值）。
     */
    private BigDecimal quantity;

    /**
     * 单位。
     */
    private String productUnit;

    /**
     * 流水类型字典 value（注解翻译用 mapper，前端只用 inoutTypeLabel）。
     */
    private String flowType;

    /**
     * 业务类型文案（外购白条 / 收货入库 / 分割出库 …；字典 {@code djs_flow_type} 翻译）。
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "flowType", other = "djs_flow_type")
    private String inoutTypeLabel;

    /**
     * 操作人 ID（注解翻译用 mapper，前端只用 operatorName）。
     */
    private Long operatorId;

    /**
     * 记录人姓名（注解翻译）。
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /**
     * 供应商名称（service / mapper JOIN 回填）。
     */
    private String supplierName;

    /**
     * 出库去向字典 value（{@code djs_stock_out_dest}；注解翻译用 mapper，前端只用 stockOutDestLabel）。
     * 入库流水恒空。
     */
    private String stockOutDest;

    /**
     * 出库去向文案（厨房 / 矿山 / 门店 / 盘点计损 …；字典 {@code djs_stock_out_dest} 翻译）。
     * 出库记录卡（r84）原「调整人」位显示此文案；入库记录卡此字段空。
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "stockOutDest", other = "djs_stock_out_dest")
    private String stockOutDestLabel;

}
