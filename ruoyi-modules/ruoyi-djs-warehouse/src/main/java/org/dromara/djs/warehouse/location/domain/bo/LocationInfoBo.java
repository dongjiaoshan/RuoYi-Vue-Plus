package org.dromara.djs.warehouse.location.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.warehouse.location.domain.LocationInfo;

import java.math.BigDecimal;

/**
 * 库位入参 BO（WMS-MD-001）。
 *
 * <p>{@code locationCode} 由前端用户手填（与药品 BRD-MED-001 一致，不走 SYS-INFRA-004 编码生成器；
 * 客户口语已用"冻品库 / 鲜品库 / 干燥库 / 药品库 / 饲料库"等业务名而非系统码）。</p>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = LocationInfo.class, reverseConvertGenerate = false)
public class LocationInfoBo extends BaseEntity {

    /**
     * 库位 ID（编辑时必填）。
     */
    private Long id;

    /**
     * 库位业务码（新增时必填，编辑时后端强制锁回旧值）。
     */
    @NotBlank(message = "{location.code.required}", groups = OnCreate.class)
    @Size(max = 32, message = "{location.code.size}")
    private String locationCode;

    /**
     * 库位名称。
     */
    @NotBlank(message = "{location.name.required}")
    @Size(max = 64, message = "{location.name.size}")
    private String locationName;

    /**
     * 库位类型（字典 {@code djs_location_type}）。
     */
    @NotBlank(message = "{location.type.required}")
    @Size(max = 32, message = "{location.type.size}")
    private String locationType;

    /**
     * 缩略图 OSS objectName。
     */
    @Size(max = 512, message = "{location.thumb.size}")
    private String locationThumb;

    /**
     * 原图 OSS objectNames（逗号分隔）。
     */
    @Size(max = 2048, message = "{location.img.size}")
    private String locationImg;

    /**
     * 状态（1 启用 / 2 停用）。
     */
    private Integer locationStatus;

    /**
     * 容量（kg / m³）。
     */
    @PositiveOrZero(message = "{location.capacity.positive}")
    private BigDecimal capacity;

    /**
     * 备注。
     */
    @Size(max = 500, message = "{location.remark.size}")
    private String remark;

    /**
     * 新增校验组（仅 {@code locationCode} 等创建时必填字段使用；编辑时忽略）。
     */
    public interface OnCreate {
    }

}
