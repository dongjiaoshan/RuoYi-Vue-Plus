package org.dromara.djs.plant.crop.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 作物 picker 轻量 VO（MP-PICKERS-001）。
 *
 * <p>给 mp {@code CropPicker} 用——农事录入选作物。只暴露 id + cropName + cropCode
 * 3 字段，对应表 {@code t_plant_crop_info}。</p>
 *
 * <p>不复用 admin {@code CropInfoVo}，避免品种 / 周期 / 图片等无关重字段进 mp 流量。</p>
 *
 * @author djs
 * @since MP-PICKERS-001
 */
@Data
public class CropPickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作物 ID（snowflake，Jackson 全局序列化为 string）。
     */
    private Long id;

    /**
     * 作物名称（如 白菜）。
     */
    private String cropName;

    /**
     * 作物业务码。
     */
    private String cropCode;

}
