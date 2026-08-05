package org.dromara.djs.store.returns.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.format.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.store.returns.domain.StoreReturn;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店退回管理 VO（admin 列表 / 详情 / 导出共用，STR-RETURN-001）。
 *
 * <p>{@code storeName} / {@code productName} 由 service 内存聚合批量填（避免 N+1）；
 * {@code operatorName} 走 {@code USER_ID_TO_NICKNAME}（契约 4.5）。</p>
 *
 * @author djs
 * @since STR-RETURN-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = StoreReturn.class)
public class StoreReturnVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @ExcelProperty(value = "退回单号")
    private String returnNo;

    @ExcelProperty(value = "退回方向")
    private String returnDirection;

    private Long storeId;

    /** 门店名称（service 内存聚合填）。 */
    @ExcelProperty(value = "退回门店")
    private String storeName;

    private Long productId;

    /** 产品名称（service 内存聚合填）。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /**
     * 产品归属类型 djs_belong_type（service 内存聚合填）：pork/white_bar=猪肉 tab，其余=果蔬 tab。
     * 前端「退回记录」猪肉/果蔬分 tab 归属由此字段决定（不再靠前端 listProduct 分页 join，避免产品超页丢归属默认成果蔬）。
     */
    private String belongType;

    /** 产品业务编码（product_info.product_id，service 内存聚合填，原型「产品代码」列）。 */
    private String productCode;

    /** 产品规格（service 内存聚合填）。 */
    private String productSpec;

    /** 产品单位（service 内存聚合填，原型「单位」列）。 */
    private String productUnit;

    /**
     * 产品<b>原材料单位</b>（service 内存聚合填；无原材料 / 原材料无单位时回落产品自身单位）。
     *
     * <p>「仓库实收量」的计量口径由它决定（甲方 row13/row14 统一模型）：
     * 原材料单位 = kg → {@code received_weight} 是<b>重量</b>，按 kg 3 位小数展示；
     * 原材料单位 ≠ kg → {@code received_weight} 是<b>件数</b>，按 {@code productUnit} 整数展示。</p>
     */
    private String materialUnit;

    private Long locationId;

    /** 入库库位名称（service 内存聚合填，STR-RETURN-REBUILD-001 K4 联动外购入库目标库位）。 */
    @ExcelProperty(value = "入库库位")
    private String locationName;

    @ExcelProperty(value = "退回数量")
    private BigDecimal returnQuantity;

    /** 报退货物重量(kg)（原型「货物重量」）。 */
    @ExcelProperty(value = "货物重量")
    private BigDecimal goodsWeight;

    /** 退货状态 djs_store_return_status：pending=待仓库确认 / received=已入库。 */
    @ExcelProperty(value = "退回状态")
    private String returnStatus;

    /** 仓库实收量（原型「仓库实收量」）。 */
    @ExcelProperty(value = "仓库实收量")
    private BigDecimal receivedQty;

    /** 仓库实收重量(kg)（原型「仓库实收重量」）。 */
    @ExcelProperty(value = "仓库实收重量")
    private BigDecimal receivedWeight;

    /**
     * 仓库确认处置：{@code 0}=退回入库 / {@code 1}=产品丢弃（admin row204 明细「是否丢弃」列）。
     *
     * <p>未确认（pending）行取建表默认 0，前端结合 {@code returnStatus} 判断是否已有处置结论。</p>
     */
    private Integer isDiscard;

    /** 仓库确认时间。 */
    @ExcelProperty(value = "确认时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;

    @ExcelProperty(value = "退回原因")
    private String returnReason;

    @ExcelProperty(value = "追溯码")
    private String traceCode;

    @ExcelProperty(value = "退回日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime returnDate;

    @ExcelProperty(value = "会员ID")
    private Long memberId;

    private Long operatorId;

    /** 经手人姓名（USER_ID_TO_NICKNAME，契约 4.5）。 */
    @ExcelProperty(value = "经手人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    private String remark;

    @ExcelProperty(value = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
