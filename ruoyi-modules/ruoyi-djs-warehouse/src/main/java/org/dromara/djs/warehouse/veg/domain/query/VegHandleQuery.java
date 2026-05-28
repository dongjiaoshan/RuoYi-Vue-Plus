package org.dromara.djs.warehouse.veg.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 毛菜处理汇总查询参数（admin 列表筛选）。
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
public class VegHandleQuery {

    private Long plotId;

    private Long cropId;

    private Long plantingRecordId;

    /**
     * 状态 djs_veg_handle_status。
     */
    private String handleStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pickStartTimeFrom;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pickStartTimeTo;

}
