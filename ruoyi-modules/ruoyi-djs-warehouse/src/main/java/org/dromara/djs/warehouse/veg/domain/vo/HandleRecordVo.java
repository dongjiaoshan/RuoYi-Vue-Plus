package org.dromara.djs.warehouse.veg.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.veg.domain.HandleRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 毛菜处理流水 VO（WMS-VEG-001 admin 详情下钻 + mp myRecords）。
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = HandleRecord.class)
public class HandleRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    private Long handleId;

    private Long plotId;

    private Long cropId;

    @ExcelProperty(value = "记录类型 1采收/2处理")
    private Integer recordType;

    @ExcelProperty(value = "本次重量(kg)")
    private BigDecimal recordWeight;

    private Integer isWeighed;

    private Integer isFinish;

    @ExcelProperty(value = "处理目标 1入库/2月台/3饲料")
    private Integer handleTarget;

    private Long locationId;

    @ExcelProperty(value = "入库库位")
    private String locationName;

    private Long handleUser;

    /**
     * 操作人名称（USER_ID_TO_NAME，对齐 skill cross-layer §契约 4.5）。
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "handleUser")
    @ExcelProperty(value = "处理人")
    private String handleUserName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "处理时间")
    private Date handleTime;

    private String proofOssIds;

    @ExcelProperty(value = "备注")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
