package org.dromara.djs.breed.event.eartag.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 出生重订正入参（V6 行243）。
 *
 * <p>分娩录入提交时整窝仔猪已自动建档（V6 行242），本入参只做「按耳号改出生重」，
 * 不新建耳号、不改头数。同一窝可反复提交，结果幂等。</p>
 *
 * @author djs
 * @since V6-R243
 */
@Data
public class PigletBirthWeightBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联分娩记录 ID。 */
    @NotNull(message = "{pigletno.farrow_id.required}")
    private Long farrowId;

    /** 待订正的仔猪（耳号 + 新出生重）。 */
    @NotEmpty(message = "{pigletno.items.required}")
    @Size(max = 50, message = "{pigletno.piglets.too_many}")
    @Valid
    private List<PigletBirthWeightItem> items;
}
