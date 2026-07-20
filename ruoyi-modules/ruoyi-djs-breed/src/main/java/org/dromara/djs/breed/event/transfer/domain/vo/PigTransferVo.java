package org.dromara.djs.breed.event.transfer.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.event.transfer.domain.PigTransfer;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 转移记录视图（BRD-EVENT-004 TRANSFER）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigTransfer.class)
public class PigTransferVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime transferDate;

    private Long oldBarnId;
    private Long oldPenId;
    private String oldBarnName;
    private String oldPenName;
    private Long newBarnId;
    private Long newPenId;
    private String newBarnName;
    private String newPenName;
    private String transferReason;
    private Long operatorId;

    /** 转移人员姓名（FIX-CC-PERSON-001 K111：operator_id → USER_ID_TO_NICKNAME 翻译；记录列表显名不显 ID）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
