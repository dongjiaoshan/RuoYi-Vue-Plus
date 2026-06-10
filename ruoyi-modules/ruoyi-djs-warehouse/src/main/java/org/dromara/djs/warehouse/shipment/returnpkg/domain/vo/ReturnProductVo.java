package org.dromara.djs.warehouse.shipment.returnpkg.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
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

    @ExcelProperty(value = "退货单号")
    private String returnNo;

    private Long storeId;

    @ExcelProperty(value = "申请时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime applyTime;

    private Long productId;

    @ExcelProperty(value = "产品名称")
    private String productName;

    @ExcelProperty(value = "退货重量")
    private BigDecimal returnWeight;

    @ExcelProperty(value = "确认重量")
    private BigDecimal confirmWeight;

    private Long confirmUser;

    /** 确认人姓名（USER_ID_TO_NICKNAME，契约 4.5）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "confirmUser")
    private String confirmUserName;

    @ExcelProperty(value = "确认时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;

    @ExcelProperty(value = "是否确认")
    private Integer isConfirm;

    @ExcelProperty(value = "退货原因")
    private String returnReason;

    @ExcelProperty(value = "退货方向")
    private String returnDirection;

    @ExcelProperty(value = "退货状态")
    private String returnStatus;

    private String proofOssIds;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
