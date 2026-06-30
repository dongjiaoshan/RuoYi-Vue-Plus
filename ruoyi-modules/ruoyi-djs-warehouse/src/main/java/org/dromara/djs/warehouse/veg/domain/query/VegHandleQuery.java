package org.dromara.djs.warehouse.veg.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

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

    /**
     * 状态多选（R70 处理状态下拉多选）。非空时按 IN 过滤，优先于单值 handleStatus。
     */
    private List<String> handleStatuses;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pickStartTimeFrom;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pickStartTimeTo;

}
