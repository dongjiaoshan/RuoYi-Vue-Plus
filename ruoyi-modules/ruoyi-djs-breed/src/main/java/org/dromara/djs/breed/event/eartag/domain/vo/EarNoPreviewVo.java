package org.dromara.djs.breed.event.eartag.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 仔猪耳号「预览」VO（FIX-BRD-MP-PIGLET-EARTAG-BE-001 / K122）。
 *
 * <p>给 mp 仔猪耳号录入页：在「确认提交」前按 farrowId 推算公/母两组下一批连号，逐头预填到录入行，
 * 替代「提交后生成」占位。<b>仅预览不占号</b>——真实分配仍在提交时走 Redisson 锁 + UNIQUE 兜底，
 * 故并发下预览号可能与最终号差若干（多人同窝同时贴标）。前端须标「预计耳号」，以提交回显为准。</p>
 *
 * <p>耳号为全号（{@code {品系1}-{品种2}-{公母1}-{出生yyMMdd6}-{序号4}}，如 {@code 1-01-1-260611-0011}），
 * 与耳标实物一致；mp 展示层按需短显。</p>
 *
 * @author djs
 * @since FIX-BRD-MP-PIGLET-EARTAG-BE-001
 */
@Data
public class EarNoPreviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 公猪组预计耳号串（长度 = 入参 maleCount），按序号升序。 */
    private List<String> maleEarNos;

    /** 母猪组预计耳号串（长度 = 入参 femaleCount），按序号升序。 */
    private List<String> femaleEarNos;
}
