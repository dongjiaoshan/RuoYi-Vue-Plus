package org.dromara.djs.breed.core.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigCreateBo;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.domain.query.PigQuery;
import org.dromara.djs.breed.core.domain.vo.PigDetailVo;
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
}
