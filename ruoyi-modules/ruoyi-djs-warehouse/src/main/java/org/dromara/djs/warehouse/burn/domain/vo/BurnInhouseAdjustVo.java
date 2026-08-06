package org.dromara.djs.warehouse.burn.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 燎毛间产品重量调整列表行 VO（V6-R43）。
 *
 * <p>一行 = 一条燎毛间产品入库产出行（{@code t_warehouse_product_inhouse}）。</p>
 *
 * @author djs
 * @since V6-R43
 */
@Data
public class BurnInhouseAdjustVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品入库行主键（= {@code t_warehouse_product_inhouse.id}，调整入参用）。
     */
    private Long id;

    /**
     * 入库时间（= {@code produce_time}，燎毛提交时录的工序时间；前端显示到时分）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date inboundTime;

    /**
     * 产品名称。
     */
    private String productName;

    /**
     * 产品入库重量 kg。
     */
    private BigDecimal productWeight;

    /**
     * 计量单位（燎毛间入库恒 kg，兜底显示用）。
     */
    private String productUnit;

    /**
     * 猪只耳号（外购白条为空）。
     */
    private String earNo;

    /**
     * 白条状态（字典 {@code djs_bar_status}，排障用；是否可调整以 {@link #burnFinished} 为准）。
     */
    private String barStatus;

    /**
     * 猪只燎毛间是否处理完成：1=是（已点「处理完成」，不可再调整）/ 0=否。
     *
     * <p>判据 = {@code bar_info.status} 是否还在 {@code pending_singe / singing} 两个燎毛间在制态。</p>
     */
    private Integer burnFinished;

    /**
     * 入库库位名称。
     */
    private String locationName;

    /**
     * 是否调整（字典 {@code djs_yes_no}：1=是 / 0=否）。
     */
    private Integer isAdjusted;

    /**
     * 入库人 ID（= 燎毛记录 {@code operator_id}，由 mp EmployeePicker 指定）。
     */
    private Long operatorId;

    /**
     * 入库人姓名。
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /**
     * 调整时间（未调整过为空）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date adjustTime;

    /**
     * 调整人 ID（未调整过为空）。
     */
    private Long adjustBy;

    /**
     * 调整人姓名。
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "adjustBy")
    private String adjustByName;

    /**
     * 猪只接收重量 kg（= {@code bar_info.arrive_weight}，燎毛间称重录的头皮肉重量；未称重为空）。
     * 调整弹框顶部只读展示。
     */
    private BigDecimal arriveWeight;

    /**
     * 该白条已入库产品重量合计 kg（含本行）。
     */
    private BigDecimal inboundedWeight;

    /**
     * 待入库重量 kg = 猪只接收重量 − 该白条已入库产品重量合计（含本行），钳 0。
     * 未称重（{@link #arriveWeight} 为空）时为空。调整弹框顶部只读展示。
     */
    private BigDecimal pendingWeight;

}
