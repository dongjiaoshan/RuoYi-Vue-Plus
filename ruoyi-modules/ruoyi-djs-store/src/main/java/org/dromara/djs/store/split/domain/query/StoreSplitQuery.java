package org.dromara.djs.store.split.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 门店白条分割查询参数（admin 列表筛选，固定 source='store' 由 service 内置）。
 *
 * @author djs
 * @since STR-SPLIT-001
 */
@Data
public class StoreSplitQuery {

    /**
     * 分割部位字典 {@code djs_pig_cut_part} 精确匹配（可选）。
     */
    private String cutPart;

    /**
     * 生产日期起（区间下界，可选）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date produceDateStart;

    /**
     * 生产日期止（区间上界，可选）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date produceDateEnd;

}
