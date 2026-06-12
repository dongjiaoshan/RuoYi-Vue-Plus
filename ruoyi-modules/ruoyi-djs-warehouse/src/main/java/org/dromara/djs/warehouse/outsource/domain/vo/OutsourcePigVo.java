package org.dromara.djs.warehouse.outsource.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.outsource.domain.OutsourcePig;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 外购猪只视图对象（DJS-FIX-WMS-RALN）。
 *
 * <p>列表列：购买日期 / 到场时间 / 猪只标识号 / 猪只重量 / 供应商 / 购买人。
 * supplierName 由 service 层按 supplierId 批量回填；buyerName 走 @Translation 翻译。</p>
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = OutsourcePig.class)
public class OutsourcePigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 购买日期。
     */
    @ExcelProperty(value = "购买日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date purchaseDate;

    /**
     * 到场时间。
     */
    @ExcelProperty(value = "到场时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date arriveTime;

    /**
     * 猪只标识号。
     */
    @ExcelProperty(value = "猪只标识号")
    private String pigMarkNo;

    /**
     * 毛猪重量 kg。
     */
    @ExcelProperty(value = "猪只重量")
    private BigDecimal pigWeight;

    /**
     * 送宰日期。
     */
    @ExcelProperty(value = "送宰日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date slaughterDate;

    /**
     * 供应商 ID。
     */
    private Long supplierId;

    /**
     * 供应商名称（service 层按 supplierId 批量回填）。
     */
    @ExcelProperty(value = "供应商")
    private String supplierName;

    /**
     * 购买人 ID（{@code sys_user.user_id}），供 {@link Translation} 翻译成 buyerName。
     */
    private String buyer;

    /**
     * 购买人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "购买人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "buyer")
    private String buyerName;

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
