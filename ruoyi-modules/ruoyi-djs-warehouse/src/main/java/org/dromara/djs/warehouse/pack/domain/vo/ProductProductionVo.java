package org.dromara.djs.warehouse.pack.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 发货产品生产记录 VO（WMS-PACK-001 admin 列表 + 导出）。
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProductProduction.class)
public class ProductProductionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "生产编号")
    private String produceNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "生产日期")
    private Date produceDate;

    private Long productId;

    @ExcelProperty(value = "产品名称")
    private String productName;

    @ExcelProperty(value = "产品类型 1=自产/2=外购/3=礼盒")
    private Integer productType;

    @ExcelProperty(value = "单位")
    private String productUnit;

    @ExcelProperty(value = "规格")
    private String productSpec;

    private Long plotId;

    @ExcelProperty(value = "蔬菜来源地块")
    private String plotName;

    @ExcelProperty(value = "来源耳号")
    private String earNo;

    @ExcelProperty(value = "序号")
    private Integer productSort;

    @ExcelProperty(value = "重量(kg)/数量")
    private BigDecimal productWeight;

    private Long storeId;

    @ExcelProperty(value = "需求门店")
    private String storeName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "生产时间")
    private Date produceTime;

    @ExcelProperty(value = "是否清点 1=是/0=否")
    private Integer isDeliveryCheck;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "清点时间")
    private Date deliveryCheckTime;

    @ExcelProperty(value = "是否到货确认 1=是/0=否")
    private Integer isArrivalConfirm;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "到货确认时间")
    private Date arrivalConfirmTime;

    private Long whiteBarId;

    private Long materialId;

    @ExcelProperty(value = "原材料耗用(kg)")
    private BigDecimal materialConsume;

    private Long supplierId;

    @ExcelProperty(value = "生产位置 location_info FK")
    private Long produceLocation;

    @ExcelProperty(value = "发货方式 1=发货/2=邮寄/3=销售")
    private Integer deliverType;

    @ExcelProperty(value = "去向 platform=发货月台/gift=礼盒")
    private String deliverDest;

    @ExcelProperty(value = "打包状态")
    private String packStatus;

    @ExcelProperty(value = "凭证图IDs")
    private String proofOssIds;

    @ExcelProperty(value = "追溯码")
    private String traceCode;

    @ExcelProperty(value = "备注")
    private String remark;

    private Long createBy;

    @ExcelProperty(value = "录入人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String createByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
