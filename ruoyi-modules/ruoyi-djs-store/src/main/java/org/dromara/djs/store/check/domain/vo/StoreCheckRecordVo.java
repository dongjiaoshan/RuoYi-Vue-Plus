package org.dromara.djs.store.check.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.store.check.domain.StoreCheckRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 门店盘点明细 line VO（STR-STOCK-001）。
 *
 * <p>{@code checkByName} 走 ruoyi {@code USER_ID_TO_NICKNAME} 翻译（NicknameTranslationImpl 取 sys_user.nick_name 中文名）。
 * {@code storeName} 由 service 层 JOIN {@code t_md_store} 回填。</p>
 *
 * @author djs
 * @since STR-STOCK-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = StoreCheckRecord.class)
public class StoreCheckRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "盘点单号")
    private String checkId;

    private Long storeId;

    /**
     * 门店名称（service 层 JOIN 回填）。
     */
    @ExcelProperty(value = "门店")
    private String storeName;

    private Long productId;

    @ExcelProperty(value = "产品")
    private String productName;

    @ExcelProperty(value = "单位")
    private String productUnit;

    @ExcelProperty(value = "系统量")
    private BigDecimal sysStock;

    @ExcelProperty(value = "实盘量")
    private BigDecimal checkStock;

    @ExcelProperty(value = "差异")
    private BigDecimal diffStock;

    @ExcelProperty(value = "结果类型")
    @ExcelDictFormat(dictType = "djs_check_result")
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
    @ExcelDictFormat(dictType = "djs_check_status")
    private String checkStatus;

    private Integer isHeader;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
