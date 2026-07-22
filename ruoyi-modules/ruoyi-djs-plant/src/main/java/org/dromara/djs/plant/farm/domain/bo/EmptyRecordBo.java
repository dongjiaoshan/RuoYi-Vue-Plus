package org.dromara.djs.plant.farm.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 空地阶段农事录入 BO（PLT-WORK-001）。
 *
 * <p>覆盖 3 类：{@code tillage_break}（翻耕） / {@code tillage_prepare}（整地） / {@code fertilize}（施肥）。</p>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Data
public class EmptyRecordBo {

    /** 农事类型（djs_farm_work_type 空地段：tillage_break / tillage_prepare / fertilize）。 */
    @NotBlank(message = "{plant.farm.type.required}")
    @Size(max = 32)
    private String farmType;

    /** 地块 ID。 */
    @NotNull(message = "{plant.farm.plot.required}")
    private Long plotId;

    /** 处理班组 ID。 */
    @NotNull(message = "{plant.farm.team.required}")
    private Long farmBy;

    /** 处理班组全集（G1-TEAMS-MULTISELECT，row37）。非空时写中间表，旧单列 farmBy 取第一个；为 null 时退化为单值 farmBy。 */
    private java.util.List<Long> farmByIds;

    /** 农事日期。 */
    @NotNull(message = "{plant.farm.date.required}")
    private LocalDate farmDate;

    /** 整地类型（可选；mp 已取消该录入项，保留字段兼容历史数据/Excel 导出列）。 */
    @Size(max = 32)
    private String tillageType;

    /** 整地方式（可选；mp 已取消该录入项，保留字段兼容历史数据/Excel 导出列）。 */
    @Size(max = 32)
    private String tillageMethod;

    /** 凭证图 OSS ids（逗号分隔）。 */
    @Size(max = 500)
    private String proofOssIds;

    @Size(max = 500)
    private String remark;
}
