package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 栋舍 × 猪只头数聚合出参（BRD-FIX-MP-PIGSELECT-001）。
 *
 * <p>专供 {@code GET /applet/pig/barn-count}——mp 端 PigSelectPanel 顶部「栋舍 chip」
 * 行的快筛数据源。每个 chip 显示 {@code barnName(count)}（如 "配怀1栋(12)"），点击后
 * 用 {@code barnCode} 回过头调 {@code /applet/pig/search?barnCode=} 过滤卡片网格。</p>
 *
 * <p>聚合维度：{@code WHERE current_status != 'END'}（或调用方给定的 statusFilter/pigTypeFilter）
 * 下，按 {@code barn_id} 分组 count，再 enrich barnName/barnCode。barn_id 为 null 的猪只
 * 不计入任何 chip（无栋舍归属，工人靠耳号搜索找）。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-PIGSELECT-001
 */
@Data
public class PigBarnCountVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 栋舍编码（业务码，chip 点击后作 search 的 barnCode filter）。 */
    private String barnCode;

    /** 栋舍名称（chip 主显示，如 "配怀1栋"）。 */
    private String barnName;

    /** 该栋舍在当前筛选维度下的猪只头数（chip 括号里的数字）。 */
    private Integer count;
}
