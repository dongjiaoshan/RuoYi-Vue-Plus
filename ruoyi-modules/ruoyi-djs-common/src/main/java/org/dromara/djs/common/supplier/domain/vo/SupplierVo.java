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
import java.util.Date;

/**
 * 供应商主数据视图对象（SYS-MD-003）。
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
     * 供应商类型。
     */
    @ExcelProperty(value = "类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_supplier_type")
    private String supplierType;

    /**
     * 联系人姓名。
     */
    @ExcelProperty(value = "联系人")
    private String contactName;

    /**
     * 联系电话。
     */
    @ExcelProperty(value = "联系电话")
    private String contactPhone;

    /**
     * 地址。
     */
    @ExcelProperty(value = "地址")
    private String address;

    /**
     * 业务状态（1 启用 / 0 停用）。
     */
    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_normal_disable")
    private Integer businessStatus;

    /**
     * 结算方式。
     */
    @ExcelProperty(value = "结算方式")
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
     * 备注。
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间。
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
