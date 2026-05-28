package org.dromara.djs.warehouse.cut.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.cut.domain.PigCutRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 分割工序记录 VO（WMS-PIG-002 admin 列表展示 + 导出）。
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigCutRecord.class)
public class PigCutRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "分割单号")
    private String cutId;

    @ExcelProperty(value = "白条编号")
    private String barId;

    private Long whiteBarId;

    @ExcelProperty(value = "猪只耳号")
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "白条领用时间")
    private Date pickupTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "分割开始时间")
    private Date cutStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "出库完成时间")
    private Date cutDoneTime;

    @ExcelProperty(value = "领用重量(kg)")
    private BigDecimal pickupWeight;

    @ExcelProperty(value = "滴水损失(kg)")
    private BigDecimal dripLoss;

    @ExcelProperty(value = "排酸时长(min)")
    private Integer acidRemoveMinutes;

    private Long operatorId;

    @ExcelProperty(value = "操作人")
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "operatorId")
    private String operatorName;

    private Long locationId;

    @ExcelProperty(value = "入冻品库位")
    private String locationName;

    private Long targetStoreId;

    private Long targetDemandId;

    @ExcelProperty(value = "是否半扇 1=是/2=否")
    private Integer isHalf;

    @ExcelProperty(value = "状态")
    private String cutStatus;

    @ExcelProperty(value = "凭证图IDs")
    private String proofOssIds;

    @ExcelProperty(value = "备注")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
