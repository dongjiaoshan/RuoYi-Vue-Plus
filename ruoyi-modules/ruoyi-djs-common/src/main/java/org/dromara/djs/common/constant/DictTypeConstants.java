package org.dromara.djs.common.constant;

/**
 * djs 业务字典类型常量（39 类）。
 *
 * <p>所有业务代码使用本常量引用 {@code sys_dict_type.dict_type} 字符串，
 * 避免 30+ 个下游 ticket 硬编码 "djs_pig_breed" 之类字符串散落。</p>
 *
 * <p>数据源：{@code script/sql/djs/V202605201000__SYS-INIT-002-init-dict.sql}。
 * 若该 SQL 增删 dict_type，本文件必须同步更新；二者数量须严格一致（39）。</p>
 *
 * <p>分组与 SQL 章节对应：</p>
 * <ul>
 *   <li>A. 系统通用（6 类）</li>
 *   <li>B. 养殖域（8 类）</li>
 *   <li>C. 种植域（10 类）</li>
 *   <li>D. 仓库域（4 类）</li>
 *   <li>E. 门店域（4 类）</li>
 *   <li>F. 追溯域（1 类）</li>
 *   <li>G. 调度域（1 类）</li>
 *   <li>H. 通用扩展（5 类，含跨域盘点状态）</li>
 * </ul>
 *
 * @author djs
 * @since SYS-INFRA-005
 */
public final class DictTypeConstants {

    private DictTypeConstants() {
    }

    /** 业务字典统一前缀，用于 {@code dict_type LIKE 'djs_%'} 过滤。 */
    public static final String DJS_PREFIX = "djs_";

    // ---------------- A. 系统通用（6 类） ----------------

    /** 用户状态：0 在职 / 1 离职 / 2 试用。 */
    public static final String USER_STATUS = "djs_user_status";

    /** 系统角色 code：boss / manager / admin / pig_keeper / planter / warehouse_keeper / store_clerk / store_manager / dispatcher / butcher / packer / driver / customer。 */
    public static final String ROLE_CODE = "djs_role_code";

    /** 农场状态：0 启用 / 1 停用。 */
    public static final String FARM_STATUS = "djs_farm_status";

    /** 门店状态：0 启用 / 1 停用 / 2 装修中。 */
    public static final String STORE_STATUS = "djs_store_status";

    /** 供应商类型：feed / breed / med / seed / pack / other。 */
    public static final String SUPPLIER_TYPE = "djs_supplier_type";

    /** 通用是否：1 是 / 0 否。 */
    public static final String YES_NO = "djs_yes_no";

    // ---------------- B. 养殖域（8 类） ----------------

    /** 猪只性别：male / female。 */
    public static final String PIG_GENDER = "djs_pig_gender";

    /** 猪只品种：duroc / landrace / yorkshire / pic / binary / ternary / other。 */
    public static final String PIG_BREED = "djs_pig_breed";

    /** 猪只生命周期阶段（9 状态，与 BRD-CORE-001 状态机 enum 必须严格一致）。 */
    public static final String PIG_LIFECYCLE = "djs_pig_lifecycle";

    /** 猪只状态事件（驱动状态机的事件 code）。 */
    public static final String PIG_STATUS_EVENT = "djs_pig_status_event";

    /** 淘汰原因。 */
    public static final String ELIMINATION_REASON = "djs_elimination_reason";

    /** 猪舍类型。 */
    public static final String BARN_TYPE = "djs_barn_type";

    /** 栏位类型（栏 / 隔栏 / 单体栏 等）。 */
    public static final String PEN_TYPE = "djs_pen_type";

    /** 兽药类型。 */
    public static final String MED_TYPE = "djs_med_type";

    // ---------------- C. 种植域（10 类） ----------------

    /** 作物科属。 */
    public static final String CROP_FAMILY = "djs_crop_family";

    /** 作物类型。 */
    public static final String CROP_TYPE = "djs_crop_type";

    /** 地块类型。 */
    public static final String PLOT_TYPE = "djs_plot_type";

    /** 土壤类型。 */
    public static final String SOIL_TYPE = "djs_soil_type";

    /** 土壤肥力。 */
    public static final String SOIL_FERTILITY = "djs_soil_fertility";

    /** 地形条件。 */
    public static final String TERRAIN_CONDITION = "djs_terrain_condition";

    /** 排水条件。 */
    public static final String DRAIN_CONDITION = "djs_drain_condition";

    /** 光照条件。 */
    public static final String LIGHT_CONDITION = "djs_light_condition";

    /** 耕作类型。 */
    public static final String TILLAGE_TYPE = "djs_tillage_type";

    /** 耕作方式。 */
    public static final String TILLAGE_WAY = "djs_tillage_way";

    // ---------------- D. 仓库域（4 类） ----------------

    /** 仓库类型。 */
    public static final String WAREHOUSE_TYPE = "djs_warehouse_type";

    /** 出入库流水类型。 */
    public static final String STOCK_FLOW_TYPE = "djs_stock_flow_type";

    /** 农事工作类型（饲料投放 / 施药 / 施肥 等）。 */
    public static final String FARM_WORK_TYPE = "djs_farm_work_type";

    /** 包装类型。 */
    public static final String PACK_TYPE = "djs_pack_type";

    // ---------------- E. 门店域（4 类） ----------------

    /** 会员等级。 */
    public static final String MEMBER_LEVEL = "djs_member_level";

    /** 商品状态。 */
    public static final String PRODUCT_STATUS = "djs_product_status";

    /** 退货原因。 */
    public static final String RETURN_REASON = "djs_return_reason";

    /** 订阅消息类型（小程序 subscribeMessage templateId 用途分类）。 */
    public static final String SUBSCRIBE_MESSAGE_TYPE = "djs_subscribe_message_type";

    // ---------------- F. 追溯域（1 类） ----------------

    /** 追溯事件类型（出生 / 投药 / 出栏 / 屠宰 / 加工 / 入库 / 售卖 等）。 */
    public static final String TRACE_EVENT_TYPE = "djs_trace_event_type";

    // ---------------- G. 调度域（1 类） ----------------

    /** 派工优先级。 */
    public static final String DISPATCH_PRIORITY = "djs_dispatch_priority";

    // ---------------- H. 通用扩展（4 类） ----------------

    /** 需求业务来源。 */
    public static final String DEMAND_BUSINESS = "djs_demand_business";

    /** 需求状态。 */
    public static final String DEMAND_STATUS = "djs_demand_status";

    /** 自然灾害类型。 */
    public static final String DISASTER_TYPE = "djs_disaster_type";

    /** 有机认证状态。 */
    public static final String ORGANIC_CERT_STATUS = "djs_organic_cert_status";

    /** 盘点状态（跨域：t_warehouse_check_record / t_store_check_record.check_status，draft/in_progress/completed）。 */
    public static final String CHECK_STATUS = "djs_check_status";
}
