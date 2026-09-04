package org.dromara.djs.plant.farmmap.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 把图上一个格子挂到一块地上（PLT-FARMMAP-001）。
 *
 * @author djs
 * @since PLT-FARMMAP-001
 */
@Data
public class FarmMapBindBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 图上格子业务码（regions.generated.ts 的 key）。
     */
    @NotBlank(message = "格子编码不能为空")
    private String regionKey;

    /**
     * 要挂的地块 ID。
     */
    @NotNull(message = "地块不能为空")
    private Long plotId;

}
