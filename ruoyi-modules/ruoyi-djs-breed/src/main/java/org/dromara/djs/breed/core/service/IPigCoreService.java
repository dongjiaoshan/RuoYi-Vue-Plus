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
     * {@code statusFilter} CSV 形式，例 {@code "HB,PZ,PH"}；空串/null = 不过滤状态但默认排除 END。
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
     * @param barnCode     栋舍编码精确过滤（{@code null}/空 = 不限栋舍；PigSelectPanel chip 点击后传）
     * @param limit         最多返回行数（1-100，默认 20）
     * @return 轻量 PigSearchVo 列表（含 ageDays/parity/lastEventDays）
     */
    List<PigSearchVo> searchByEarKeyword(String earNoKeyword,
                                         String statusFilter,
                                         String sexFilter,
                                         String pigTypeFilter,
                                         String barnCode,
                                         Integer limit);

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
