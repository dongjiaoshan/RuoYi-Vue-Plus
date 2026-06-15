package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 耳号简版 → 全版映射 VO（详情父系/母系显示全版耳号 enrich，批量反查避免 N+1）。
 *
 * <p>仅内部使用：{@code PigMapper.selectEarTagByEarNos} 按短号集合批量反查 ear_tag，
 * 供 {@code queryDetail} 回填 {@code PigVo.motherEarTag / fatherEarTag}。不直接出参给前端。</p>
 *
 * @author djs
 */
@Data
public class PigEarTagMapVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 耳号简版（业务键）。 */
    private String earNo;

    /** 耳号全版 {breed}-{strain}-{sex}-{yyMMdd}-{seq:03d}。 */
    private String earTag;
}
