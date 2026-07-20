package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 育肥猪日龄分布饼图单段 VO（FIX-MGMT-MP-BRD-001，原型"育肥 tab·育肥猪日龄分布饼图 6 段"）。
 *
 * <p>每段 = 日龄区间中文标签 {@code label} + 该区间在养育肥猪头数 {@code count}。
 * 日龄边界口径见 #7.6（保育期 &lt;43 / 43-70 / 71-135 / 136-210 / 211-245 / 245+）。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-BRD-001
 */
@Data
public class AgeBucketVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 日龄区间中文标签（如"保育期(<43天)"）。 */
    private String label;

    /** 该区间在养育肥猪头数。 */
    private Integer count;

    public AgeBucketVo() {
    }

    public AgeBucketVo(String label, Integer count) {
        this.label = label;
        this.count = count == null ? 0 : count;
    }
}
