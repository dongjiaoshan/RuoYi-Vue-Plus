package org.dromara.djs.breed.event.slaughter.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 批量出栏待录入猪只（mp 批量出栏录入页逐头列重量用）。
 *
 * <p>mp 批量选择页只回传 {@code pigId} 数组（雪花 string），录入页要按「靠左耳号 + 靠右重量输入框」
 * 逐头渲染 → 用本 VO 按 pigId 批量换耳号。返回顺序与入参 pigIds 顺序一致（工人选择顺序 = 录入顺序）。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
public class SlaughterBatchPigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 猪只 ID（雪花，Jackson 序列化为 string）。 */
    private Long pigId;

    /** 猪只耳号。 */
    private String earNo;
}
