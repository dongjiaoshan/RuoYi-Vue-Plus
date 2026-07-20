package org.dromara.djs.warehouse.location.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 库位 picker 轻量 VO（MP-UX-002）。
 *
 * <p>给 mp {@code LocationPicker} 用——只暴露 id + locationName + locationCode + locationType
 * 4 字段，足够 picker 列表显示 + locationType filter。</p>
 *
 * <p>不复用 {@link LocationInfoVo}，避免 thumb / img / 容量 / 审计字段等无关字段进 mp 流量。</p>
 *
 * @author djs
 * @since MP-UX-002
 */
@Data
public class LocationPickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库位 ID（snowflake，Jackson 序列化为 string）。
     */
    private Long id;

    /**
     * 库位业务码（如 LOC0001）。
     */
    private String locationCode;

    /**
     * 库位名称。
     */
    private String locationName;

    /**
     * 库位类型（字典 {@code djs_location_type}，给 picker 显示二级标签 + 业务页面过滤用）。
     */
    private String locationType;

}
