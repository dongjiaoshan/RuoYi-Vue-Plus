package org.dromara.djs.plant.organic.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 关联地块的轻量引用对象（仅 id + name），用于 PlotOrganicVo.relatedPlots 列表。
 *
 * @author djs
 * @since PLT-MD-003
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlotRefVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long plotId;

    private String plotName;
}
