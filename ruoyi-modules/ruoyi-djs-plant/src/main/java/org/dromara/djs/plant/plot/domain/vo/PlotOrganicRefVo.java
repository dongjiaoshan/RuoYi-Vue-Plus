package org.dromara.djs.plant.plot.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 地块详情·认证信息子表单行（FIX-PLT-AD-DETAIL-001，按 plotId 透视）。
 *
 * <p>映射 {@code t_plant_organic_plotno op} JOIN {@code t_plant_plot_organic o}，
 * 列对齐原型「认证信息」子表：证书编号 / 证书单位 / 有效期 / 是否预警。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-DETAIL-001
 */
@Data
public class PlotOrganicRefVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 证书 id（{@code t_plant_plot_organic.id}）。 */
    private Long organicId;

    /** 证书编号。 */
    private String organicNo;

    /** 颁发单位。 */
    private String organicCompany;

    /** 有效期。 */
    private LocalDate organicValid;

    /** 是否预警（字典 {@code djs_yes_no}：1=预警 / 2=正常）。 */
    private Integer isWarning;
}
