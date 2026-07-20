package org.dromara.djs.breed.event.eartag.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 某次分娩的耳标统计 VO（BRD-EVENT-003）。
 *
 * <p>小程序"选分娩 → 显示活产 / 已贴 / 待贴 + 已贴清单"用。</p>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
@Data
public class FarrowEarTagStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分娩记录 ID。 */
    private Long farrowId;

    /** 母猪 pig_id。 */
    private Long motherPigId;

    /** 母猪耳号。 */
    private String motherEar;

    /** 分娩日期。 */
    private LocalDateTime farrowDate;

    /** 胎次。 */
    private Integer parity;

    /** 活产数（live_born）。 */
    private Integer liveBorn;

    /** 已贴耳标头数。 */
    private Integer tagged;

    /** 待贴耳标头数（liveBorn - tagged）。 */
    private Integer remaining;

    /** 已贴清单（按 tag_date asc）。 */
    private List<PigletEarTagVo> taggedList;
}
