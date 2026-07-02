package org.dromara.djs.warehouse.cut.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.cut.domain.PigCutRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 分割工序记录 VO（WMS-PIG-002 admin 列表展示 + 导出）。
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigCutRecord.class)
public class PigCutRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "分割单号")
    private String cutId;

    @ExcelProperty(value = "白条编号")
    private String barId;

    private Long whiteBarId;

    /** 半只白条流水号（分割按半只展示；空=整猪旧记录）。 */
    @ExcelProperty(value = "白条流水号")
    private String whiteBarNo;

    @ExcelProperty(value = "猪只耳号")
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "白条领用时间")
    private Date pickupTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "分割开始时间")
    private Date cutStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "出库完成时间")
    private Date cutDoneTime;

    @ExcelProperty(value = "领用重量(kg)")
    private BigDecimal pickupWeight;

    /**
     * 剩余可分割重量 kg（= pickupWeight − 已入库分割产品重量合计，service 计算回填）。
     * 不入库（仅运行期展示），故无 {@code @ExcelProperty}。
     */
    private BigDecimal remainingWeight;

    // ===== P5 外购标注（compute-on-read，按 white_bar_id 关联 bar_info 派生，无持久列）=====

    /**
     * 是否外购普通白条（派生：bar_info.supplier_id != null）。
     * true = 外购普通白条（无耳号，展示供应商名）；false/null = 自养（展示耳号）。
     */
    private Boolean isOutsource;

    /**
     * 供应商名称（仅外购，按 bar_info.supplier_id 批量回填；自养为 null）。
     */
    @ExcelProperty(value = "供应商")
    private String supplierName;

    /**
     * 外购猪只标识号（{@code bar_info.mark_id}，仅外购回填；FIX-WMS-OUTSOURCE-001 行53）。
     *
     * <p>外购无耳号（{@code ear_no=NULL}），分割单 chip 原回退显 CUT 业务码（{@code cutId}）。
     * 改为优先显该外购猪只的白条编号 {@code barId} / 标识号 {@code markId}，让外购记录用
     * 「外购白条编号」而非 CUT 码标识。前端 chip 显示优先级：{@code earNo ?? markId ?? barId ?? cutId}。</p>
     */
    private String markId;

    // ===== P7 统计指标（compute-on-read，按 white_bar_id 关联 bar_info + product_inhouse 派生，无持久列）=====

    /**
     * 头皮肉出品率（= bar.arriveWeight / bar.marketingWeight）。
     * 分母为 0 或缺失时返 null（前端显「—」）。
     */
    @ExcelProperty(value = "头皮肉出品率")
    private BigDecimal headSkinYieldRate;

    /**
     * 白条出品率（= bar.inWeight / bar.arriveWeight）。
     * 分母为 0 或缺失时返 null。
     */
    @ExcelProperty(value = "白条出品率")
    private BigDecimal whiteBarYieldRate;

    /**
     * 预冷损耗量 kg（= bar.inWeight − bar.outWeight）。
     */
    @ExcelProperty(value = "预冷损耗量(kg)")
    private BigDecimal precoolLossWeight;

    /**
     * 预冷损耗率（= (bar.inWeight − bar.outWeight) / bar.inWeight）。
     * 分母为 0 或缺失时返 null。
     */
    @ExcelProperty(value = "预冷损耗率")
    private BigDecimal precoolLossRate;

    /**
     * 冷库停留时长（分钟，= bar.outTime − bar.inTime）。
     * 任一时间缺失时返 null。
     */
    @ExcelProperty(value = "冷库停留(min)")
    private Long coldStorageMinutes;

    /**
     * 分割品总重 kg（= Σ product_inhouse.product_weight，white_bar_id 匹配且 cut_part 非空）。
     */
    @ExcelProperty(value = "分割品总重(kg)")
    private BigDecimal cutProductTotalWeight;

    /**
     * 分割损耗 kg（= bar.outWeight − 分割品总重）。
     */
    @ExcelProperty(value = "分割损耗(kg)")
    private BigDecimal cutLossWeight;

    @ExcelProperty(value = "滴水损失(kg)")
    private BigDecimal dripLoss;

    @ExcelProperty(value = "排酸时长(min)")
    private Integer acidRemoveMinutes;

    private Long operatorId;

    @ExcelProperty(value = "操作人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    private Long locationId;

    @ExcelProperty(value = "入冻品库位")
    private String locationName;

    private Long targetStoreId;

    private Long targetDemandId;

    @ExcelProperty(value = "是否半扇 1=是/2=否")
    private Integer isHalf;

    @ExcelProperty(value = "状态")
    private String cutStatus;

    @ExcelProperty(value = "凭证图IDs")
    private String proofOssIds;

    @ExcelProperty(value = "备注")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
