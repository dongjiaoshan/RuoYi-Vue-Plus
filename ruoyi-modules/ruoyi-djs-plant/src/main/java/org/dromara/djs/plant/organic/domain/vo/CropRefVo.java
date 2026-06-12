package org.dromara.djs.plant.organic.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 关联作物的轻量引用对象（仅 id + name），用于 CropOrganicVo.relatedCrops 列表。
 *
 * <p>与 {@link PlotRefVo} 土地证书-地块引用同范式（一证多作物 el-transfer 回显）。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-INFO-LIST-001
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CropRefVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long cropId;

    private String cropName;
}
