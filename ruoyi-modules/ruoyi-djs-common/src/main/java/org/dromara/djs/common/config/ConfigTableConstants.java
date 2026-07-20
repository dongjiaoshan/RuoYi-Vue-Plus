package org.dromara.djs.common.config;

import java.util.List;

/**
 * 客户可调业务配置表的聚合面声明（CROSS-DICT-001）。
 *
 * <p>本类集中声明"哪些 config 表纳入业务配置版本聚合"，以及每张表参与 hash 的
 * <b>业务参数列</b>（不含 6 个审计字段 + tenant_id + del_unique）。版本号 = 这些表
 * 业务参数列实时值的 SHA-256 hash，admin 改任一行参数 → 下次探测自然算出新 hash，
 * 小程序据此重拉。</p>
 *
 * <p>聚合面"动态枚举"的实现：本表是编译期常量白名单（同 {@code DictTypeConstants}
 * 反射 djs 字典常量的思路），实施时已落地的客户可调 config 表逐一登记于此；未落地的
 * 模块（如 plant config）落地后在此追加一行即纳入，无需改 service / mapper 逻辑。</p>
 *
 * <p><b>为什么不哈希审计列</b>：{@code create_time / update_time} 等会随每次保存抖动
 * （即便参数值没变 {@code update_time=NOW()}），纳入聚合会让 hash 不再是"配置值的纯
 * 函数"——改回原值 hash 回不去、无谓保存触发 mp 重拉。聚合对象只取客户能调的参数值
 * 本身。{@code del_flag / tenant_id} 仅作 WHERE 过滤条件、不进 hash 体。</p>
 *
 * @author djs
 * @since CROSS-DICT-001
 */
public final class ConfigTableConstants {

    private ConfigTableConstants() {
    }

    /**
     * 一张可调 config 表的聚合声明。
     *
     * @param table      物理表名（{@link #CONFIG_TABLES} 白名单内的常量，非用户输入，拼接进 SQL 安全）
     * @param orderBy    行间稳定排序键（保证多行 hash 稳定；通常用业务键 + id 兜底）
     * @param valueColumns 参与 hash 的业务参数列（不含审计 / tenant / del_unique）
     */
    public record ConfigTable(String table, String orderBy, List<String> valueColumns) {
    }

    /**
     * 生产周期配置（BRD-MD-003）：出栏日龄 / 妊娠天数 / 断奶天数等客户可调周期参数。
     */
    public static final ConfigTable PRODUCTION_CYCLE = new ConfigTable(
        "t_farm_production_cycle_config",
        "config_key, id",
        List.of("config_key", "default_value", "custom_value", "unit", "description")
    );

    /**
     * 育种配置（BRD-MD-001）：品系 + 母 / 父 / 仔代编码规则。
     */
    public static final ConfigTable BREED = new ConfigTable(
        "t_farm_breed_config",
        "breed_strain, id",
        List.of("breed_strain", "mother_code", "father_code", "cub_code")
    );

    /**
     * 公猪 / 精液配置（BRD-MD-003）：精液质量阈值 + 配种间隔天数。
     */
    public static final ConfigTable BOAR = new ConfigTable(
        "t_farm_boar_config",
        "boar_id, id",
        List.of("boar_id", "sperm_quality_threshold", "breeding_interval_days")
    );

    /**
     * 用药计划配置（BRD-MED-003）：药品类型 + 触发事件 + 天数偏移。
     */
    public static final ConfigTable MED_SCHEDULE = new ConfigTable(
        "t_farm_med_schedule_config",
        "event_trigger, med_type, id",
        List.of("med_type", "event_trigger", "days_offset", "description")
    );

    /**
     * 全部纳入聚合的客户可调 config 表（按表名字典序排序，保证跨进程 hash 稳定）。
     *
     * <p>新增可调 config 表时在此追加一行即纳入版本聚合；plant config（PLT-MD-003）
     * 当前无独立可调 config 表（organic-cert 不是参数表），落地后在此追加。</p>
     */
    public static final List<ConfigTable> CONFIG_TABLES = List.of(
        BOAR,
        BREED,
        MED_SCHEDULE,
        PRODUCTION_CYCLE
    );
}
