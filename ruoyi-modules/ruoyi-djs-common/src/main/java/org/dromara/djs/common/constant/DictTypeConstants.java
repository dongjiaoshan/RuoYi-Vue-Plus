package org.dromara.djs.common.constant;

/**
 * djs 业务字典类型常量（33 类）。
 *
 * <p>所有业务代码使用本常量引用 {@code sys_dict_type.dict_type} 字符串，
 * 避免 30+ 个下游 ticket 硬编码 "djs_pig_sex" 之类字符串散落。</p>
 *
 * <p>数据源：{@code db/migration} 下各域 dict seed。
 * 若 seed SQL 增删 dict_type，本文件必须同步更新（当前 33 类）。</p>
 *
 * <p>分组：</p>
 * <ul>
 *   <li>A. 系统通用（4 类）</li>
 *   <li>B. 养殖域（19 类）</li>
 *   <li>C. 种植域（2 类）</li>
 *   <li>D. 仓库域（5 类）</li>
 *   <li>H. 通用扩展（3 类，含跨域盘点状态）</li>
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

    // ---------------- A. 系统通用（4 类） ----------------

    /** 农场状态：0 启用 / 1 停用。 */
    public static final String FARM_STATUS = "djs_farm_status";

    /** 门店状态：0 启用 / 1 停用 / 2 装修中。 */
    public static final String STORE_STATUS = "djs_store_status";

    /** 供应商类型：feed / breed / med / seed / pack / other。 */
    public static final String SUPPLIER_TYPE = "djs_supplier_type";

    /** 通用是否：1 是 / 0 否。 */
    public static final String YES_NO = "djs_yes_no";

    // ---------------- B. 养殖域（19 类） ----------------

    /** 猪只性别（DB 列值 M/F，对齐 doc/11 R8）。 */
    public static final String PIG_SEX = "djs_pig_sex";

    /** 猪只生命周期阶段（9 状态，与 BRD-CORE-001 状态机 enum 必须严格一致）。 */
    public static final String PIG_LIFECYCLE = "djs_pig_lifecycle";

    /** 猪只状态事件（驱动状态机的事件 code）。 */
    public static final String PIG_STATUS_EVENT = "djs_pig_status_event";

    /** 猪舍类型。 */
    public static final String BARN_TYPE = "djs_barn_type";

    /** 栏位类型（栏 / 隔栏 / 单体栏 等）。 */
    public static final String PEN_TYPE = "djs_pen_type";

    /** 兽药类型。 */
    public static final String MED_TYPE = "djs_med_type";

    /** 用药类型（mp 用药治疗/批量用药：保健 / 治疗 / 疫苗；疫苗联动二级 djs_vaccine_type）。 */
    public static final String MEDICINE_USE_TYPE = "djs_medicine_use_type";

    /** 用药方式（mp 用药：注射 / 口服 / 喷雾 等）。 */
    public static final String MEDICINE_WAY = "djs_medicine_way";

    /** 用药原因（mp 用药）。 */
    public static final String MEDICINE_REASON = "djs_medicine_reason";

    /** 疫苗类型（mp 用药类型=疫苗 时联动二级下拉）。 */
    public static final String VACCINE_TYPE = "djs_vaccine_type";

    /** 死亡原因（BRD-EVENT-004 DIE deathReason）。 */
    public static final String DEATH_REASON = "djs_death_reason";

    /** 死亡去向（BRD-EVENT-004 DIE deathDest）。 */
    public static final String DEATH_DEST = "djs_death_dest";

    /** 淘汰原因（BRD-EVENT-004 ELIMINATE cullingReason；对齐 doc/11 R11）。 */
    public static final String CULLING_REASON = "djs_culling_reason";

    /** 淘汰去向（BRD-EVENT-004 ELIMINATE cullingDest；对齐 doc/11 R12）。 */
    public static final String CULLING_DEST = "djs_culling_dest";

    /** 出栏去向（BRD-EVENT-004 SLAUGHTER marketDest；对齐 doc/11 R13）。 */
    public static final String MARKET_DEST = "djs_market_dest";

    /** 药品领用动作（BRD-MED-002：use=领用 / return=退回 / loss=损耗；与用药类型 MEDICINE_USE_TYPE 语义分离）。 */
    public static final String MED_PICK_ACTION = "djs_med_pick_action";

    /** 引种类型（BRD-EVENT-001：external 外部引种 / internal 内部引种；对齐 t_farm_pig_introduce.introduce_type）。 */
    public static final String INTRODUCE_TYPE = "djs_introduce_type";

    /** 返空流异常类型（BRD-EVENT-002 NULL_RETURN，DB 存 R 返情 / A 流产 / N 空怀，映射状态机 FQ/LC/KH）。 */
    public static final String ABNORMAL = "djs_abnormal";

    /** 默认仔猪重量（DENGBO R64/R65：出生重预填耳标页 / 断奶重预填断奶页，dict_value 存数字 kg，客户可改）。 */
    public static final String PIGLET_DEFAULT_WEIGHT = "djs_piglet_default_weight";

    // ---------------- C. 种植域（2 类） ----------------

    /** 作物科属。 */
    public static final String CROP_FAMILY = "djs_crop_family";

    /** 地块类型。 */
    public static final String PLOT_TYPE = "djs_plot_type";

    // ---------------- D. 仓库域（5 类） ----------------

    /** 农事工作类型（饲料投放 / 施药 / 施肥 等）。 */
    public static final String FARM_WORK_TYPE = "djs_farm_work_type";

    /** 出库去向（doc/11 §0.3 R33 — kitchen/mine/daye_store/personal）；WMS-MAT-001 领用、WMS-PIG-002 分割等出库流水用。 */
    public static final String STOCK_OUT_DEST = "djs_stock_out_dest";

    /** 蔬菜处理去向（WMS-VEG-001：1=入库 / 2=月台 / 3=有机饲料；对齐 t_warehouse_handle_record.handle_target TINYINT）。 */
    public static final String HANDLE_TARGET = "djs_handle_target";

    /** 有机饲喂提供位置类型（WMS-FEED-RECORD-001：veg_handle=毛菜间 / warehouse=仓库；t_warehouse_feed_log.feed_type）。 */
    public static final String FEED_TYPE = "djs_feed_type";

    /** 物资/商品分类（feed/medicine/fertilizer/pesticide/packaging/seed/equipment/raw/other；t_warehouse_product_info.buy_class，mp 物资领用「商品分类」展示+筛选码→中文）。 */
    public static final String BUY_CLASS = "djs_buy_class";

    // ---------------- H. 通用扩展（3 类，含跨域盘点状态） ----------------

    /** 需求状态。 */
    public static final String DEMAND_STATUS = "djs_demand_status";

    /** 自然灾害类型。 */
    public static final String DISASTER_TYPE = "djs_disaster_type";

    /** 盘点状态（跨域：t_warehouse_check_record / t_store_check_record.check_status，draft/in_progress/completed）。 */
    public static final String CHECK_STATUS = "djs_check_status";
}
