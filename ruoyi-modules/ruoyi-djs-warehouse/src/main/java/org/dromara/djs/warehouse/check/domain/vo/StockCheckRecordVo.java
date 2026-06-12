package org.dromara.djs.warehouse.check.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.warehouse.check.domain.StockCheckRecord;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 盘点明细 line VO（WMS-STOCK-001）。
 *
 * <p>{@code checkByName} 走 ruoyi {@code USER_ID_TO_NICKNAME} 翻译（NicknameTranslationImpl 取 sys_user.nick_name 中文名）。
 * {@code locationName} 由 service 层 JOIN 回填。</p>
 *
 * @author djs
 * @since WMS-STOCK-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = StockCheckRecord.class)
public class StockCheckRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "盘点单号")
    private String checkId;

    private Long locationId;

    /**
     * 库位名称（service 层 JOIN 回填）。
     */
    @ExcelProperty(value = "库位")
    private String locationName;

    private Long productId;

    /**
     * 产品代码（业务码，service 层按 productId 回填 {@code ProductInfo.productId}）。
     */
    @ExcelProperty(value = "产品代码")
    private String productCode;

    @ExcelProperty(value = "产品名称")
    private String productName;

    @ExcelProperty(value = "单位")
    private String productUnit;

    @ExcelProperty(value = "库存量")
    private BigDecimal sysStock;

    @ExcelProperty(value = "盘点库存量")
    private BigDecimal checkStock;

    /**
     * 盘点计损量（正常结果时计入的损耗量 = {@code |sysStock - checkStock|}；异常时为 0；service 层派生）。
     */
    @ExcelProperty(value = "盘点计损量")
    private BigDecimal lossWeight;

    @ExcelProperty(value = "差异")
    private BigDecimal diffStock;

    @ExcelProperty(value = "盘点结果")
    private Integer checkResultType;

    @ExcelProperty(value = "差异原因")
    private String diffReason;

    private Long checkBy;

    /**
     * 盘点人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "盘点人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "checkBy")
    private String checkByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "盘点日期")
    private Date checkDate;

    @ExcelProperty(value = "状态")
    private String checkStatus;

    private Integer isHeader;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
