package org.dromara.djs.store.operation.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.store.operation.domain.StoreSaleRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 门店销售流水 VO（STR-OP-001）。
 *
 * <p>{@code storeName} 由 service 层 JOIN {@code t_md_store} 回填；{@code operatorName} 走 ruoyi
 * {@code USER_ID_TO_NICKNAME} 翻译（NicknameTranslationImpl 取 sys_user.nick_name 中文名）。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = StoreSaleRecord.class)
public class StoreSaleRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    private Long storeId;

    /**
     * 门店名称（service 层 JOIN 回填）。
     */
    @ExcelProperty(value = "门店")
    private String storeName;

    private Long productId;

    @ExcelProperty(value = "产品名称")
    private String productName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "销售日期")
    private Date saleDate;

    @ExcelProperty(value = "销售数量")
    private BigDecimal saleQty;

    @ExcelProperty(value = "单位")
    private String saleUnit;

    @ExcelProperty(value = "销售总额")
    private BigDecimal saleAmount;

    private Long operatorId;

    /**
     * 录入人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "录入人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /**
     * 数据来源（字典 {@code djs_sale_source}：manual / excel_import）。
     */
    @ExcelProperty(value = "数据来源")
    private String source;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

    private String remark;

}
