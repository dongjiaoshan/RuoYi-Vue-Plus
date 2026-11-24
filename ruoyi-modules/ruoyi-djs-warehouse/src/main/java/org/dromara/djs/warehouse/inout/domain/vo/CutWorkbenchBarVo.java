package org.dromara.djs.warehouse.inout.domain.vo;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
@Data
public class CutWorkbenchBarVo {
    private Long barInfoId;
    private Long inhouseId;
    private Long cutRecordId;
    private String whiteBarNo;
    private String earNo;
    /** 首次接收日减出生日期，缺少来源生日或接收记录时为空。 */
    private Integer ageDays;
    private String productName;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private Date inTime;
    private BigDecimal inWeight;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private Date operateTime;
    private BigDecimal remainingWeight;
    private String cutStatus;
}
