package org.dromara.djs.warehouse.pigbuy.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.pigbuy.domain.PigPurchase;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 外购猪只到货登记 VO（FIX-WMS-MP-PIGBUY-001）。
 *
 * <p>{@code operatorName} 走 ruoyi {@code USER_ID_TO_NAME} 翻译（5.5.x 实现 {@code UserNameTranslationImpl}）。
 * 来源 / 状态 label 由 mp 端字典渲染（{@code djs_pig_source} / {@code djs_pig_purchase_status}）。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-PIGBUY-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigPurchase.class)
public class PigPurchaseVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "到货单号")
    private String purchaseNo;

    @ExcelProperty(value = "来源类型")
    private String sourceType;

    @ExcelProperty(value = "到货数量")
    private Integer quantity;

    @ExcelProperty(value = "到货重量(kg)")
    private BigDecimal arriveWeight;

    @ExcelProperty(value = "供应商")
    private String supplierName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "到货时间")
    private Date arriveTime;

    @ExcelProperty(value = "处理状态")
    private String purchaseStatus;

    private Long operatorId;

    /**
     * 登记人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "登记人")
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "operatorId")
    private String operatorName;

    @ExcelProperty(value = "凭证图IDs")
    private String proofOssIds;

    @ExcelProperty(value = "备注")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
