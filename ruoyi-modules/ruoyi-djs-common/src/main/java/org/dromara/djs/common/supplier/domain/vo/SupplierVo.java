package org.dromara.djs.common.supplier.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.djs.common.supplier.domain.Supplier;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 供应商主数据视图对象（SYS-MD-003 + SYS-MD-FIX-002）。
 *
 * @author djs
 * @since SYS-MD-003
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = Supplier.class)
public class SupplierVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 供应商 ID。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 供应商编码。
     */
    @ExcelProperty(value = "供应商编码")
    private String supplierCode;

    /**
     * 供应商名称。
     */
    @ExcelProperty(value = "供应商名称")
    private String supplierName;

    /**
     * 营业执照编号。
     */
    @ExcelProperty(value = "营业执照编号")
    private String licenseNo;

    /**
     * 营业执照图片（OSS oss_id；前端 image-preview 用）。
     */
    private Long licenseImageOssId;

    /**
     * 经营许可证编号。
     */
    @ExcelProperty(value = "经营许可证编号")
    private String businessLicenseNo;

    /**
     * 合作开始日期。
     */
    @ExcelProperty(value = "合作开始日期")
    private LocalDate cooperationStartDate;

    /**
     * 供应商类型（字典 djs_supplier_type）。
     */
    @ExcelProperty(value = "类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_supplier_type")
    private String supplierType;

    /**
     * 联系负责人姓名。
     */
    @ExcelProperty(value = "联系负责人")
    private String liaisonName;

    /**
     * 负责人电话。
     */
    @ExcelProperty(value = "负责人电话")
    private String liaisonPhone;

    /**
     * 地址。
     */
    @ExcelProperty(value = "地址")
    private String address;

    /**
     * 合作状态（字典 djs_supplier_status）。
     */
    @ExcelProperty(value = "合作状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_supplier_status")
    private String businessStatus;

    /**
     * 结算方式（字典 djs_settle_type）。
     */
    @ExcelProperty(value = "结算方式", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_settle_type")
    private String settleType;

    /**
     * 银行账户。
     */
    @ExcelProperty(value = "银行账户")
    private String bankAccount;

    /**
     * 开户行。
     */
    @ExcelProperty(value = "开户行")
    private String bankName;

    /**
     * 交易次数（V1 stub=0，下游 BRD-MED / WMS-PURCHASE 落地后回填）。
     */
    @ExcelProperty(value = "交易次数")
    private Integer dealCount;

    /**
     * 累计购入商品数（V1 stub=0）。
     */
    @ExcelProperty(value = "购入数量")
    private BigDecimal purchaseQty;

    /**
     * 备注。
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间。
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 更新时间。
     */
    private Date updateTime;

}
