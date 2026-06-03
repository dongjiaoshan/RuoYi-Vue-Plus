package org.dromara.djs.warehouse.burn.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 已出栏待燎毛入库白条 minimal VO（D12X-MP-BURN-IA-001 mp 列表页用）。
 *
 * <p>燎毛间 IA 重做：进页 = 「已出栏未入库猪只列表」 = bar_info 中
 * {@code status IN ('pending_singe','singing')} 的白条（出栏后由
 * {@link org.dromara.djs.warehouse.cross.listener.PigMarketingEventListener}
 * 自动 INSERT，尚未推进到 in_stock）。点某项 → 入库子页按 4 类型分别入库。</p>
 *
 * <p>Long 字段（id）经 ruoyi JacksonConfig 自动序列化为 string（snowflake 截断防御），
 * mp TS 类型对应 string。</p>
 *
 * @author djs
 * @since D12X-MP-BURN-IA-001
 */
@Data
public class BarPendingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 白条主键（snowflake，序列化为 string；mp 列表→子页传此 id）。
     */
    private Long id;

    /**
     * 白条业务码（如 BAR2606030001）。
     */
    private String barId;

    /**
     * 猪只耳号。
     */
    private String earNo;

    /**
     * 出栏时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date marketingTime;

    /**
     * 出栏重量 kg。
     */
    private BigDecimal marketingWeight;

    /**
     * 白条状态（字典 djs_bar_status：pending_singe 待燎毛 / singing 燎毛中）。
     */
    private String status;

}
