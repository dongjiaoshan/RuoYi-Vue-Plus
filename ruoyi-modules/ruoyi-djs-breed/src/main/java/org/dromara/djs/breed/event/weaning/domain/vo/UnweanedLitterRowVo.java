package org.dromara.djs.breed.event.weaning.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 「分娩未断奶母猪 + 其未断奶仔猪」扁平行（BRD-WEAN-SELECT-001，V6 行238）。
 *
 * <p>一行 = 一头未断奶仔猪（带所属窝 / 母猪冗余列），由
 * {@code PigWeaningMapper.selectUnweanedLitterRows} 一次 JOIN 查出，service 侧按 farrowId 归并成
 * {@link UnweanedLitterVo}。扁平出参是为了一条 SQL 拿全（避免逐窝 N+1）。</p>
 *
 * @author djs
 * @since BRD-WEAN-SELECT-001
 */
@Data
public class UnweanedLitterRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分娩记录 ID。 */
    private Long farrowId;

    /** 母猪 pig_info.id。 */
    private Long sowPigId;

    /** 母猪耳号。 */
    private String sowEarNo;

    /** 分娩日期。 */
    private LocalDateTime farrowDate;

    /** 分娩当时胎次。 */
    private Integer parity;

    /** 母猪出生日期（service 侧算实时日龄用）。 */
    private LocalDate sowBirthDate;

    /** 母猪引种日期（无出生日期时算日龄的回落基准）。 */
    private LocalDate sowIntroduceDate;

    /** 母猪当前栋舍编码（录入页转移目标默认值）。 */
    private String barnCode;

    /** 母猪当前栋舍中文名。 */
    private String barnName;

    /** 母猪当前栏位编码。 */
    private String penCode;

    /** 母猪当前栏位中文名。 */
    private String penName;

    /** 仔猪耳号。 */
    private String pigletEarNo;

    /** 仔猪性别 F=母 / M=公。 */
    private String pigletSex;
}
