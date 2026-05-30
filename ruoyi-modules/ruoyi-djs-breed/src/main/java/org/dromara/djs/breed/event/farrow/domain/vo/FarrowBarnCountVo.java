package org.dromara.djs.breed.event.farrow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分娩栋舍 × 待打标窝数聚合 VO（BRD-FIX-MP-EVENT-MISC-IA-001 — 仔猪耳号选窝 chip）。
 *
 * <p>原型 96 顶部分娩栋舍 chip "分娩1栋(12)"——按 farrow.barn_name 聚合仍有未贴满标的窝数。
 * 点击 chip 后 mp 端把 barnName 传回 {@code queryPendingLitters} 作过滤。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-EVENT-MISC-IA-001
 */
@Data
public class FarrowBarnCountVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分娩栋舍名（chip 主显示 + 点击后作过滤 key）。 */
    private String barnName;

    /** 该栋舍下待打标窝数（chip 括号里的数字）。 */
    private Integer count;
}
