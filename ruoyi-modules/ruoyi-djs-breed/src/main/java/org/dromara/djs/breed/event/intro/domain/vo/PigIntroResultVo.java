package org.dromara.djs.breed.event.intro.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 引种端点返回 VO（BRD-EVENT-001）。
 *
 * <p>{@link #introduce} 业务行；{@link #pigs} 实际创建的猪只摘要（含 id + earNo + currentStatus）。
 * mp 端拿到后可立即跳详情 / 弹"引种成功，耳号 XXX" toast。</p>
 *
 * @author djs
 * @since BRD-EVENT-001
 */
@Data
public class PigIntroResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 引种业务表行。 */
    private PigIntroduceVo introduce;

    /** 创建的猪只清单（单头时 size=1，批量时 size=N）。 */
    private List<PigSummary> pigs;

    /** 内嵌类型：猪只摘要（避免返完整 Pig 实体把内部字段全暴露给 mp）。 */
    @Data
    public static class PigSummary {

        private Long id;

        private String earNo;

        private String earTag;

        private String pigSex;

        private String pigType;

        /** 创建后 lifecycle 状态：F→HB / M→BOAR_ACTIVE。 */
        private String currentStatus;
    }
}
