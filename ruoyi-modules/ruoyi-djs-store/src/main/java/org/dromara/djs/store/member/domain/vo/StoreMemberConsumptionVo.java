package org.dromara.djs.store.member.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.store.member.domain.StoreMemberConsumption;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 会员手录消费记录 VO（STR-MEMBER-001）。
 *
 * <p>{@code operatorName} 走 {@code USER_ID_TO_NICKNAME} 翻译，mapper 指向 {@code createBy}——本表无独立
 * operator_id 列，录入人即建档审计的 {@code create_by}（ADR-0007）。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = StoreMemberConsumption.class)
public class StoreMemberConsumptionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    private Long memberId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "消费日期")
    private Date consumeDate;

    private Long storeId;

    @ExcelProperty(value = "商品SKU")
    private String sku;

    @ExcelProperty(value = "数量")
    private BigDecimal quantity;

    @ExcelProperty(value = "金额")
    private BigDecimal amountManual;

    @ExcelProperty(value = "备注")
    private String notes;

    private Long createBy;

    /**
     * 录入人姓名（注解翻译，本表无 operator_id 列，录入人即 create_by）。
     */
    @ExcelProperty(value = "录入人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
