package org.dromara.djs.breed.farm.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.farm.domain.FarmInfo;

import java.io.Serial;
import java.io.Serializable;

/**
 * 农场主数据视图对象（BRD-MD-002）。
 *
 * <p>用于 {@code GET /djs/breed/farm-info/current} 返回当前农场详情（V1 hardcode id=1001）。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Data
@AutoMapper(target = FarmInfo.class)
public class FarmInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 农场 ID。
     */
    private Long id;

    /**
     * 农场编码。
     */
    private String farmCode;

    /**
     * 农场名称。
     */
    private String farmName;

    /**
     * 农场状态（字典 {@code djs_farm_status}：0=启用 / 1=停用）。
     */
    private Integer farmStatus;

    /**
     * 联系人。
     */
    private String contactName;

    /**
     * 联系电话。
     */
    private String contactPhone;

    /**
     * 地址。
     */
    private String address;

    /**
     * 备注。
     */
    private String remark;

}
