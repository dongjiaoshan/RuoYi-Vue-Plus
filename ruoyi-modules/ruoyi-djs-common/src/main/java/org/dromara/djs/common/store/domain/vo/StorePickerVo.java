package org.dromara.djs.common.store.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店 picker 轻量 VO（MP-UX-002）。
 *
 * <p>给 mp StorePicker 用——只暴露选择必须的 3 个字段：</p>
 * <ul>
 *   <li>{@code id} — snowflake string（Jackson 全局 Long → String 序列化）</li>
 *   <li>{@code storeName} — UI 主标签</li>
 *   <li>{@code storeCode} — UI 次标签（如 ST0001）</li>
 * </ul>
 *
 * <p>不复用 {@link StoreVo}，避免 manager / pos / image / 审计字段等 12 个无关字段进 mp 流量。</p>
 *
 * @author djs
 * @since MP-UX-002
 */
@Data
public class StorePickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店 ID（snowflake，Jackson 序列化为 string）。
     */
    private Long id;

    /**
     * 门店名称。
     */
    private String storeName;

    /**
     * 门店编码（业务码，如 ST0001）。
     */
    private String storeCode;

}
