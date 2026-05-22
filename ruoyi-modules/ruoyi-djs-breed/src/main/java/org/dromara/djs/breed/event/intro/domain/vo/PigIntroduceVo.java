package org.dromara.djs.breed.event.intro.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 引种业务表 VO（BRD-EVENT-001）。
 *
 * @author djs
 * @since BRD-EVENT-001
 */
@Data
public class PigIntroduceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String introduceNo;

    private String introduceType;

    private LocalDate introduceDate;

    private Long supplierId;

    private Integer pigCount;

    private String startEarNo;

    private String pigBreedCode;

    private String pigStrainCode;

    private String pigSex;

    private String proofOssIds;

    private Long barnId;

    private Long penId;

    private String remark;

    private Date createTime;
}
