package org.dromara.djs.breed.core.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigCreateBo;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.domain.query.PigQuery;
import org.dromara.djs.breed.core.domain.query.PigStatusRecordQuery;
import org.dromara.djs.breed.core.domain.vo.PigDetailVo;
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
     * @param earNoKeyword 耳号关键字（LIKE 中部匹配；为空时返最近 N 条）
     * @param statusFilter 状态白名单 CSV（如 {@code "HB,DN,LC,KH,FQ"} 给配种 picker 用）
     * @param sexFilter    性别过滤（{@code "M"} / {@code "F"} / null）
     * @param pigTypeFilter 类型过滤（{@code "sow"/"boar"/"piglet"/"fattening"} / null）
     * @param limit         最多返回行数（1-100，默认 20）
     * @return 轻量 PigSearchVo 列表
     */
    List<PigSearchVo> searchByEarKeyword(String earNoKeyword,
                                         String statusFilter,
                                         String sexFilter,
                                         String pigTypeFilter,
                                         Integer limit);
}
