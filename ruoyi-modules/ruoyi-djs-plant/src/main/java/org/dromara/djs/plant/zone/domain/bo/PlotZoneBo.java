package org.dromara.djs.plant.zone.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.plant.zone.domain.PlotZone;

/**
 * 片区入参 BO（PLT-MD-001）。
 *
 * <p>{@code zoneCode} 由前端用户手填（与 LocationInfo / drug 一致，不走 SYS-INFRA-004 编码生成器；
 * 用户口语已用 A 区 / 东部温室区 等业务名）。</p>
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PlotZone.class, reverseConvertGenerate = false)
public class PlotZoneBo extends BaseEntity {

    /**
     * 片区 ID（编辑时必填）。
     */
    private Long id;

    /**
     * 片区业务码（新增时必填，编辑时后端强制锁回旧值）。
     */
    @NotBlank(message = "{plant.zone.code.required}", groups = OnCreate.class)
    @Size(max = 32, message = "{plant.zone.code.size}")
    private String zoneCode;

    /**
     * 片区名称。
     */
    @NotBlank(message = "{plant.zone.name.required}")
    @Size(max = 64, message = "{plant.zone.name.size}")
    private String zoneName;

    /**
     * 片区说明。
     */
    @Size(max = 255, message = "{plant.zone.desc.size}")
    private String zoneDesc;

    /**
     * 所属大区。
     */
    @Size(max = 64, message = "{plant.zone.belong.size}")
    private String zoneBelong;

    /**
     * 状态（字典 sys_normal_disable：1=正常 / 2=停用）。
     */
    private Integer zoneStatus;

    /**
     * 新增校验组（仅 {@code zoneCode} 新增时必填；编辑时忽略）。
     */
    public interface OnCreate {
    }
}
