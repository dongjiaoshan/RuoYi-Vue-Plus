package org.dromara.djs.warehouse.shipment.domain.vo;

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
import org.dromara.djs.warehouse.shipment.domain.Shipment;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发货流水 VO（admin 列表 / 详情 / 导出共用）。
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = Shipment.class)
public class ShipmentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @ExcelProperty(value = "发货单号")
    private String shipmentNo;

    private Long demandId;

    @ExcelProperty(value = "业态", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_belong_type")
    private String productType;

    private Long storeId;

    /** 门店名（VO 由 service fillVo 填充，不走 @Translation 因 djs 自建 t_md_store）。 */
    private String storeName;

    @ExcelProperty(value = "发货日期")
    private LocalDate shipDate;

    @ExcelProperty(value = "发货数量")
    private BigDecimal shipQuantity;

    @ExcelProperty(value = "单位")
    private String shipUnit;

    @ExcelProperty(value = "发货方式", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_deliver_type")
    private Integer deliverType;

    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;

    @ExcelProperty(value = "状态", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_shipment_status")
    private String shipmentStatus;

    private Long checkerId;

    /** 清点员姓名（@Translation 翻译，对齐契约 4.5：用 USER_ID_TO_NICKNAME 显 sys_user.nick_name 中文名）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "checkerId")
    private String checkerName;

    @ExcelProperty(value = "清点时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkTime;

    private String proofOssIds;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
