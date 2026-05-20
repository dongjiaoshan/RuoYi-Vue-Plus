package org.dromara.djs.common.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 农场轻量信息 VO（农场切换器使用）。
 *
 * V1：multi-farm disabled，{@code id} 固定为 "1001"、{@code name} 固定为 "东角山主农场"。
 * V2：从 sys_farm 表（doc/05 §4.3 多农场）读取。
 *
 * @author djs
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FarmVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 农场 ID（VARCHAR(20)，跟 djs 业务表 tenant_id / farm_id 列类型一致）
     */
    private String id;

    /**
     * 农场名称
     */
    private String name;

    /**
     * 是否为当前激活农场
     */
    private Boolean current;
}
