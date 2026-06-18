package org.dromara.djs.breed.core.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigCreateBo;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.domain.query.PigQuery;
import org.dromara.djs.breed.core.domain.query.PigStatusRecordQuery;
import org.dromara.djs.breed.core.domain.vo.PigBarnCountVo;
import org.dromara.djs.breed.core.domain.vo.PigDetailVo;
import org.dromara.djs.breed.core.domain.vo.PigIntroDetailVo;
import org.dromara.djs.breed.core.domain.vo.PigSearchVo;
import org.dromara.djs.breed.core.domain.vo.PigStatusRecordVo;
import org.dromara.djs.breed.core.domain.vo.PigVo;

import java.util.List;
import java.util.Map;

/**
 * 猪只主数据 + 状态机核心 Service（BRD-CORE-001 ★ 业务心脏）。
 *
 * <p>所有业务侧（BRD-EVENT-001~004）改变猪只状态都必须通过 {@link #fireEvent}，
 * 不允许直接 update {@code current_status}。{@code fireEvent} 内：</p>
 * <ol>
 *   <li>校验 transition 合法性（{@link PigStateMachine}）；</li>
 *   <li>INSERT t_farm_status_record；</li>
 *   <li>UPDATE t_farm_pig_info（带乐观锁 version）；</li>
 *   <li>发布 {@link org.dromara.djs.breed.core.event.PigStateChangedEvent}。</li>
 * </ol>
 *
 * @author djs
 * @since BRD-CORE-001
 */
public interface IPigCoreService {

    /**
     * 状态机统一事件入口（11 UI events 全走这；INTRO 除外）。
     *
     * @param bo 事件入参（pigId / eventType / payload / relatedEventId）
     * @return 刚写入的 status_record（含 id）
     */
    PigStatusRecordVo fireEvent(PigEventBo bo);

    /**
     * 创建猪只（INTRO 路径）：INSERT pig + INSERT 初始 status_record + 发布事件。
     *
     * @param bo 创建入参
     * @return 创建后的 Pig 实体（含 id）
     */
    Pig createPig(PigCreateBo bo);

    /**
     * 内部引种把场内肥猪转为种猪（FIX-INTRO-001 + 邓博 2026-06-18 重定类型）。
     *
     * <p>语义：内部引种把一头**已存在**的肥猪（{@code pig_type='fattening'}）登记进引种台账后，
     * 按性别重定 {@code pig_type} + 初始种猪态——母 → {@code sow}/{@code HB}（后备母猪，可进配种选猪）；
     * 公 → {@code boar} + 空状态 {@code ''}（种公猪，非种母猪无繁殖态）。否则肥猪转后备后 pig_type 仍 fattening，
     * 配种选猪（{@code pig_type='sow'}）里看不到它。本方法写 {@code t_farm_pig_status_record}
     * （old=原态 / new=种猪初始态 / event=INTRO）+ update {@code t_farm_pig}（pig_type + current_status），
     * 与调用方（{@code PigIntroServiceImpl.introduceInternal}）同一 {@code @Transactional}。</p>
     *
     * <p><b>仅对 fattening 来源猪生效</b>：已重定为 sow / boar（含本方法幂等重跑）或 piglet → 直接返回。
     * END 终态肥猪（已死亡 / 出栏）不转。{@code current_status} 空（旧脏数据）时 old=null 不抛。
     * 不走 {@link #fireEvent}——INTRO 不是状态机合法事件，故手工写 record + update，与 createPig 初始
     * record 范式一致。</p>
     *
     * @param pigId 已存在猪只 ID
     */
    void internalIntroToReserve(Long pigId);

    /** 详情：基础字段 + 最近 20 条状态变更。 */
    PigDetailVo queryDetail(Long pigId);

    /**
     * 内部引种 auto-fill 单头查猪明细（BRD-FIX-MP-INTRO-001）。
     *
     * <p>返回 mp 端「内部引种」segment 只读「猪只信息」卡所需字段：性别 label / 品种 label /
     * 品系 label / 日龄 / 当前位置（栋舍名+栏位名拼接）。所有 label 在 service 层翻好，
     * mp 端不再二次翻字典（可测试性约束：不显原始 code）。</p>
     *
     * @param pigId 猪只 ID
     * @return 明细 VO；pigId 不存在抛 ServiceException
     */
    PigIntroDetailVo queryIntroDetail(Long pigId);

    /** 分页列表（支持 excludeEnd / earNo / pigSex 等过滤）。 */
    TableDataInfo<PigVo> queryPage(PigQuery query, PageQuery pageQuery);

    /** 全量状态变更历史（DESC by change_time，最多返 200 条防爆）。 */
    List<PigStatusRecordVo> listHistory(Long pigId);

    /** 全局状态流水分页查询（admin"事件台账"页）。 */
    TableDataInfo<PigStatusRecordVo> queryStatusRecordPage(PigStatusRecordQuery query, PageQuery pageQuery);

    /**
     * mp 端 PigPicker 用——耳号关键字 + 状态/性别/类型过滤的轻量搜索（BRD-LIST-001 D6 carryover）。
     *
     * <p>语义：{@code WHERE ear_no LIKE '%earNoKeyword%' AND current_status IN (statusFilter)
     * AND pig_sex = sexFilter AND pig_type = pigTypeFilter} ORDER BY id DESC LIMIT N。</p>
     *
     * <p>所有过滤器都是可选的；都不传时返最近 {@code limit} 条非 END 猪只。
     * {@code statusFilter} CSV 形式，例 {@code "HB,PZ,FM"}；空串/null = 不过滤状态但默认排除 END。
     * 终态（{@code END}）猪只**永不返回**——picker 是给事件录入用的，END 猪只不能再触发事件。</p>
     *
     * <p>BRD-FIX-MP-PIGSELECT-001：出参 PigSearchVo 已扩 {@code ageDays/parity/lastEventDays}
     * 三个量化字段（mp 端 PigSelectPanel 卡片用），并新增可选 {@code barnCode} 过滤（栋舍 chip 快筛）。
     * 旧 PigPicker 调用方不传 barnCode 即可，行为不变。</p>
     *
     * @param earNoKeyword 耳号关键字（LIKE 中部匹配；为空时返最近 N 条）
     * @param statusFilter 状态白名单 CSV（如 {@code "HB,DN,LC,KH,FQ"} 给配种 picker 用）
     * @param sexFilter    性别过滤（{@code "M"} / {@code "F"} / null）
     * @param pigTypeFilter 类型过滤（{@code "sow"/"boar"/"piglet"/"fattening"} / null）
     * <p>D12X-MP-FARROW-WEANING-001：新增可选 {@code dueType} 到期窗口过滤（分娩 / 断奶选猪用）。
     * 在状态白名单（如 {@code statusFilter=PZ}）之上叠加一层「基准日期 + 生产周期配置天数 ≤ today」
     * 的 AND 过滤，只返回真正到了产期 / 断奶期的母猪。不传 → 行为不变（向后兼容所有现有调用方）。</p>
     *
     * @param earNoKeyword 耳号关键字（LIKE 中部匹配；为空时返最近 N 条）
     * @param statusFilter 状态白名单 CSV（如 {@code "HB,DN,LC,KH,FQ"} 给配种 picker 用）
     * @param sexFilter    性别过滤（{@code "M"} / {@code "F"} / null）
     * @param pigTypeFilter 类型过滤（{@code "sow"/"boar"/"piglet"/"fattening"} / null）
     * @param barnCode     栋舍编码精确过滤（{@code null}/空 = 不限栋舍；PigSelectPanel chip 点击后传）
     * @param limit         最多返回行数（1-100，默认 20）
     * @param dueType       到期窗口过滤（{@code "FARROW"}=已到产期 / {@code "WEANING"}=已到断奶期 / null=不过滤）
     * @param excludeNullBarn 是否排除无栋舍归属（{@code barn_id} 为 null）的猪只（FIX-BREEDING-001 #23a）。
     *                        {@code true} 时与 {@link #countByBarn} 口径一致（列表数 = 各栋舍 chip 之和）；
     *                        配种选猪场景传 true，其余调用方传 {@code null}/false 不变（向后兼容）。
     * @param minAgeDays   最小日龄过滤（天）：仅返回 {@code 日龄 >= minAgeDays} 的猪只（出栏选猪用，
     *                     按出栏配置 {@code slaughter_age_days} 限到龄肥猪）。日龄 = NOW − birth_date
     *                     （缺 birth_date 时回落 introduce_date）；两者均空的猪不满足该过滤、被剔除。
     *                     {@code null}/≤0 → 不做日龄过滤（向后兼容所有现有调用方）。
     * @return 轻量 PigSearchVo 列表（含 ageDays/parity/lastEventDays）
     */
    List<PigSearchVo> searchByEarKeyword(String earNoKeyword,
                                         String statusFilter,
                                         String sexFilter,
                                         String pigTypeFilter,
                                         String barnCode,
                                         Integer limit,
                                         String dueType,
                                         Boolean excludeNullBarn,
                                         Integer minAgeDays);

    /**
     * 加载 t_farm_breed_info 主表 code→中文名 映射（breedStrain 1=品种 / 2=品系）。
     * 供需要把品种/品系码翻成「品种品系表」权威中文名的服务（如引种记录）复用，批量预载一次防 N+1。
     * 取不到（表里无该 code）由调用方回落字典 / 原始 code（邓博 2026-06-17 #19/#22）。
     *
     * @param breedStrain 1=品种 / 2=品系
     * @return code→中文名 映射（无数据返空 map）
     */
    Map<String, String> loadBreedStrainNameMap(Integer breedStrain);

    /**
     * 栋舍 × 头数聚合（BRD-FIX-MP-PIGSELECT-001）——mp 端 PigSelectPanel 顶部「栋舍 chip」数据源。
     *
     * <p>语义：在 {@code statusFilter}（CSV，默认排除 END） + {@code sexFilter} + {@code pigTypeFilter}
     * 过滤维度下，按 {@code barn_id} 分组 count，再 enrich barnCode/barnName 返回。barn_id 为 null
     * 的猪只不计入（无栋舍归属）。结果按 barnCode 升序，count 为 0 的栋舍不返回。</p>
     *
     * @param statusFilter  状态白名单 CSV（与 search 同语义；含 END 时放行终态）
     * @param sexFilter     性别过滤（{@code "M"} / {@code "F"} / null）
     * @param pigTypeFilter 类型过滤（{@code "sow"/"boar"/"piglet"/"fattening"} / null）
     * @return 栋舍头数聚合列表
     */
    List<PigBarnCountVo> countByBarn(String statusFilter, String sexFilter, String pigTypeFilter);
}
