package org.dromara.djs.breed.event.farrow.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分娩 picker 轻量 VO（D9 closing Group D — FarrowPicker）。
 *
 * <p>专给 mp 端 {@code FarrowPicker} 用。工人先选母猪 earNo → 反查最近 5 次未贴满标的
 * 分娩记录 → list 展示（farrowDate + pigletNum + 剩余耳标数） → 选中 emit farrowId
 * （比手输 19 位 snowflake 更顺工人思维，参 _open-issues #8 决策 a）。</p>
 *
 * <p>跨层契约（skill cross-layer-contract §1 / §2）：</p>
 * <ul>
 *   <li>{@code id} 是 snowflake Long，序列化为 string（JacksonConfig 全局规则）</li>
 *   <li>mp 端 picker 展示 {@code farrowDate / pigletNum / remainEartag}，工人不直接看 id</li>
 * </ul>
 *
 * @author djs
 * @since D9-closing Group D
 */
@Data
public class FarrowPickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分娩记录主键（snowflake string）。 */
    private Long id;

    /** 母猪耳号（picker 二级展示用）。 */
    private String motherEarNo;

    /** 分娩日期（picker 主展示）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime farrowDate;

    /** 仔猪头数 = liveBorn（picker 主展示"X 头/共 Y 头"）。 */
    private Integer pigletNum;

    /** 已贴耳标数（service enrich）。 */
    private Integer taggedEartag;

    /** 剩余可贴 = pigletNum - taggedEartag。 */
    private Integer remainEartag;

    /** 当时胎次。 */
    private Integer parity;
}
