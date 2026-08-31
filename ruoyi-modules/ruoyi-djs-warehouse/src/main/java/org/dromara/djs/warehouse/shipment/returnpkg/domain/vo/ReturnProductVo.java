package org.dromara.djs.warehouse.shipment.returnpkg.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;
import cn.idev.excel.annotation.format.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.ReturnProduct;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退货管理 VO（admin 列表 / 详情 / 导出共用）。
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReturnProduct.class)
public class ReturnProductVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @ExcelProperty(value = "退回单号")
    private String returnNo;

    private Long storeId;

    /** 退货门店名称（service 批量回填，对齐原型「退货门店」列展门店名而非 id）。 */
    @ExcelProperty(value = "退回门店")
    private String storeName;

    @ExcelProperty(value = "退回日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime applyTime;

    private Long productId;

    /** 退货品类（产品 belongType 字典 djs_belong_type，service 批量回填）。 */
    @ExcelProperty(value = "退回品类", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_belong_type")
    private String returnCategory;

    /** 退货产品编号（产品业务码 product_info.product_id，service 批量回填）。 */
    @ExcelProperty(value = "退回产品编号")
    private String returnProductCode;

    @ExcelProperty(value = "退回产品")
    private String productName;

    /** 退货单位（产品 product_unit，service 批量回填）。 */
    @ExcelProperty(value = "退回单位")
    private String productUnit;

    /** 产品原材料名（产品 product_material FK → 原材料产品名；无则取自身名，service 批量回填）。 */
    @ExcelProperty(value = "产品原材料")
    private String productMaterialName;

    @ExcelProperty(value = "退回重量")
    private BigDecimal returnWeight;

    @ExcelProperty(value = "实收重量")
    private BigDecimal confirmWeight;

    /** 重量差异 = returnWeight - confirmWeight（衍生，service 回填；未确认时为 null）。 */
    @ExcelProperty(value = "重量差异")
    private BigDecimal weightDiff;

    private Long confirmUser;

    /** 确认人姓名（USER_ID_TO_NICKNAME，契约 4.5）。 */
    @ExcelProperty(value = "退回确认人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "confirmUser")
    private String confirmUserName;

    @ExcelProperty(value = "退回确认时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;

    @ExcelProperty(value = "是否确认")
    private Integer isConfirm;

    @ExcelProperty(value = "退回原因")
    private String returnReason;

    @ExcelProperty(value = "退回方向")
    private String returnDirection;

    @ExcelProperty(value = "退回状态")
    private String returnStatus;

    private String proofOssIds;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
