package org.dromara.djs.warehouse.veg.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 毛菜间处理记录列表查询条件（FIX-ADMIN-R130，仓库-admin 行130「毛菜间处理记录」只读菜单）。
 *
 * @author djs
 * @since FIX-ADMIN-R130
 */
@Data
public class VegHandleRecordQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 处理日期起（含，可空；yyyy-MM-dd）。
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateFrom;

    /**
     * 处理日期止（含，可空；yyyy-MM-dd）。
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date dateTo;

    /**
     * 作物名称（模糊）。
     */
    private String cropName;

    /**
     * 统计来源（精确）：1=毛菜处理间 / 2=采摘活动。与「采摘明细」页 statSource 同码值。
     */
    private String statSource;

    /**
     * 处理方式（精确，字典 {@code djs_pick_dest}：sale / veg_fresh / platform / loss / feed）。
     */
    private String handleMethod;

    /**
     * 地块编号（模糊，匹配 {@code t_plant_plot_info.plot_code}）。
     */
    private String plotCode;

    /**
     * 记录人（模糊，匹配 {@code sys_user.nick_name}）。
     */
    private String recorderName;
}
