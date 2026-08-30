package org.dromara.djs.warehouse.cut.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 白条领用「按燎毛产出行」卡片 VO（FIX-WMS-CUTPICKUP-SPLIT-001）。
 *
 * <p>admin 白条领用页据此每行出一张卡：一头在库白条（{@code bar.status='in_stock'}）的每个未领燎毛产出行
 * （{@code t_warehouse_product_inhouse WHERE white_bar_id=bar.id AND pickup_status=0}）= 一张卡，
 * 可单独领用进分割车间；剩余未领行继续显示，实现「录入两个半只→两条记录→分两次领用」。</p>
 *
 * <p>燎毛未产出任何 inhouse 行的白条 → 回落一张「整只」卡（{@code inhouseId=null}），领用走整猪兜底路径
 * （向后兼容旧数据 / mp 不录产出行场景）。</p>
 *
 * @author djs
 * @since FIX-WMS-CUTPICKUP-SPLIT-001
 */
@Data
public class BarPickupItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 燎毛产出行 ID（FK → {@code t_warehouse_product_inhouse.id}）。领用提交时回传，按此行消耗。
     *
     * <p>{@code null} = 该白条无燎毛产出行的「整只」兜底卡，提交时走整猪路径。</p>
     */
    private Long inhouseId;

    /**
     * 白条 ID（FK → {@code t_warehouse_bar_info.id}）。
     */
    private Long barInfoId;

    private String barId;

    private String earNo;

    /**
     * 外购白条标识号（外购无耳号时 chip 显示用）。
     */
    private String markId;

    /**
     * 白条流水号（半只/整只白条唯一标识，PC 领用界面「白条流水号」列显示；燎毛按白条生成）。
     */
    private String whiteBarNo;

    /**
     * 出栏重量 kg（领用过磅校验上界：该白条累计领用不应大于该值）。
     */
    private BigDecimal marketingWeight;

    private BigDecimal inWeight;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date inTime;

    /**
     * 燎毛间到场时间（{@code bar_info.arrive_time}）。mp 白条出库卡第 2 行左列。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date arriveTime;

    /**
     * 到场重量 kg（燎毛前过磅，{@code bar_info.arrive_weight}）。mp 白条出库卡第 2 行右列。
     */
    private BigDecimal arriveWeight;

    /**
     * 该燎毛产出行的产品名（如「白条·半只」「白条·半扇」「整只」）。整只兜底卡为「白条（整只）」。
     */
    private String productName;

    /**
     * 该产出行燎毛入库重量 kg（领用过磅默认值，前端可改）。整只兜底卡 = 出栏重量。
     */
    private BigDecimal productWeight;

    /**
     * 计量单位（kg 等）。
     */
    private String productUnit;

    /**
     * 猪只**出栏当时**日龄（天，V6 row145）。
     *
     * <p>口径 = 出栏日期 − 出生日期（缺出生日期回落引种日期），与 {@code PigAgeUtil} / ADR-0017
     * 「事件当时日龄」一致。<b>不用「距今天数」</b>：白条是已宰杀的猪，按今天算这个数会天天变大，
     * 挂在冷库里的白条过一周就多七天，没有业务意义。</p>
     *
     * <p>外购白条（无耳号）/ 耳号在猪档案里查不到 / 无出生与引种日期 → {@code null}，前端该格不渲染。</p>
     */
    private Integer ageDays;
}
