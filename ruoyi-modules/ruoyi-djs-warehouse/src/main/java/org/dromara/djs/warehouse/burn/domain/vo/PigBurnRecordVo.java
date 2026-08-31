package org.dromara.djs.warehouse.burn.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.burn.domain.PigBurnRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 燎毛工序记录 VO（WMS-PIG-001）。
 *
 * <p>{@code operatorName} 走 ruoyi {@code USER_ID_TO_NICKNAME} 翻译（NicknameTranslationImpl 取 sys_user.nick_name 中文名）。</p>
 *
 * @author djs
 * @since WMS-PIG-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigBurnRecord.class)
public class PigBurnRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "燎毛单号")
    private String burnId;

    @ExcelProperty(value = "猪只耳号")
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "燎毛时间")
    private Date burnTime;

    @ExcelProperty(value = "到场重量(kg)")
    private BigDecimal arriveWeight;

    @ExcelProperty(value = "燎毛后重量(kg)")
    private BigDecimal burnWeight;

    @ExcelProperty(value = "损耗(kg)")
    private BigDecimal lossWeight;

    @ExcelProperty(value = "状态", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_burn_status")
    private String burnStatus;

    private Long operatorId;

    /**
     * 操作人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "操作人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    private Long locationId;

    /**
     * 库位名称（service 层 JOIN 回填，VO 不走 @Translation，参照 LocationStockServiceImpl 的 fillLocationNames 模式）。
     */
    @ExcelProperty(value = "入库位")
    private String locationName;

    @ExcelProperty(value = "凭证图IDs")
    private String proofOssIds;

    @ExcelProperty(value = "备注")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
