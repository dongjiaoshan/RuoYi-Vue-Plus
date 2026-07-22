package org.dromara.djs.plant.pick.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * mp 端采摘任务卡片 VO（PLT-PLAN-002）。
 *
 * <p>映射 {@code t_plant_plant_details} 单行 + enrich plot/crop 名称。
 * 本 ticket mp 端**仅浏览，不录入**（录入闭环在 D12 PLT-PICK-001）。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Data
public class PickTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** plant_details.id（snowflake，TS 端 string）。 */
    private Long id;

    private Long plantId;
    private String planNo;

    private Long plotId;
    private String plotCode;
    private String plotName;

    private Long cropId;
    private String cropName;

    /** 计划月份 1-12（区分同地块多批次：同 plot 不同 plant_month/plant_period 是合法多条）。 */
    private Integer plantMonth;
    /** 计划阶段 CHAR(2)（字典 djs_plant_period：05=上旬 / 15=中旬 / 25=下旬）。 */
    private String plantPeriod;

    /** 种植日期（取实际开始种植 begin_actualdate）。 */
    private LocalDate plantDate;

    private LocalDate earliestHarvestdate;
    private LocalDate lastHarvestdate;
    private LocalDate beginHarvestdate;
    private LocalDate endHarvestdate;

    /** djs_pick_status：pending/picking/completed/delayed。 */
    private String harvestStatus;

    private BigDecimal expectedYield;
    private BigDecimal actualYield;

    /** 1=游客采摘活动 / 2=否（字典 djs_yes_no）。 */
    private Integer isPick;

    /**
     * 采摘活动销售分摊结算轮次（{@code t_plant_plant_details.pick_settle_round}）：
     * 0/null=未「录入完成」（当前批次），&gt;0=已随第 N 次「录入完成」参与分摊结算。
     *
     * <p>供 mp 采摘活动详情头卡从服务端恢复「采摘重量录入」按钮置灰态：该作物任一地块
     * {@code pickSettleRound>0} 即已录入完成，重进详情页按钮亦保持置灰不可再录（row223）。</p>
     */
    private Integer pickSettleRound;

    private Long harvestBy;
    private String harvestTeamName;

    /**
     * 种植班组（row40）：采收/采摘录入时默认预填此班组（可改）。取 plant_details.plant_by（多选第一个）。
     */
    private Long plantBy;
    /** 种植班组名（row40，预填展示）。 */
    private String plantTeamName;
    /** 种植班组全集 id（row40，多班组时全部预填）。 */
    private java.util.List<Long> plantByIds;
    /** 种植班组全集名（row40，多班组时全部展示）。 */
    private java.util.List<String> plantTeamNames;
    /** 采摘班组全集 id（row40 回显）。 */
    private java.util.List<Long> harvestByIds;
    /** 采摘班组全集名（row40 回显）。 */
    private java.util.List<String> harvestTeamNames;

    /**
     * 指派班组成员数（t_plant_work_people 行数）。
     *
     * <p>供 mp 区分两种空 picker：harvestBy 为空 = 未指派班组；
     * harvestBy 非空但 memberCount=0 = 指派了班组但班组无成员。</p>
     */
    private Integer memberCount;
}
