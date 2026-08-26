package org.dromara.djs.warehouse.veg.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.warehouse.veg.domain.VegetableHandle;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 毛菜处理汇总 VO（WMS-VEG-001 admin 列表 + 导出）。
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = VegetableHandle.class)
public class VegetableHandleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    private Long plantingRecordId;

    private Long plotId;

    /**
     * 冗余地块名称（service fillName 填）。
     */
    @ExcelProperty(value = "地块")
    private String plotName;

    private Long cropId;

    @ExcelProperty(value = "作物")
    private String cropName;

    private Long productId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "采摘开始时间")
    private Date pickStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "采摘结束时间")
    private Date pickEndTime;

    @ExcelProperty(value = "已摘(kg)")
    private BigDecimal pickedWeight;

    @ExcelProperty(value = "处理后(kg)")
    private BigDecimal handledWeight;

    @ExcelProperty(value = "饲料饲喂(kg)")
    private BigDecimal feedWeight;

    @ExcelProperty(value = "发往月台(kg)")
    private BigDecimal sendPlatformWeight;

    @ExcelProperty(value = "入库(kg)")
    private BigDecimal stockInWeight;

    @ExcelProperty(value = "损耗(kg)")
    private BigDecimal lossWeight;

    @ExcelProperty(value = "已称重 1是/2否")
    private Integer isWeighed;

    @ExcelProperty(value = "已完成 1是/2否")
    private Integer isFinish;

    @ExcelProperty(value = "状态", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_veg_handle_status")
    private String handleStatus;

    @ExcelProperty(value = "备注")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
