package org.dromara.djs.breed.event.intro.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBatchBo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBo;
import org.dromara.djs.breed.event.intro.domain.query.PigIntroQuery;
import org.dromara.djs.breed.event.intro.domain.vo.PigIntroResultVo;
import org.dromara.djs.breed.event.intro.domain.vo.PigIntroduceVo;

/**
 * 引种业务 Service（BRD-EVENT-001）。
 *
 * <p>调用链：</p>
 * <ol>
 *   <li>校验入参（外部引种 supplier + proof 必填 / supplier_type=breed / barn-pen 一致 / 容量充足）</li>
 *   <li>生成引种单号 {@code IBizCodeGenerator#INTRO_NO}</li>
 *   <li>INSERT {@code t_farm_pig_introduce}</li>
 *   <li>{@code IPigCoreService.createPig(PigCreateBo)} 每头 — service 内部建 pig 行 + 写初始 status_record</li>
 *   <li>{@code Pen.current_count += pigCount}（同事务原子 UPDATE）</li>
 * </ol>
 *
 * <p>事务边界：整个调用链 {@code @Transactional}；批量引种半成品场景全回滚。</p>
 *
 * @author djs
 * @since BRD-EVENT-001
 */
public interface IPigIntroService {

    /**
     * 单头引种。
     *
     * @param bo 单头入参
     * @return 引种业务行 + 创建的猪只摘要（pigs.size==1）
     */
    PigIntroResultVo introduce(PigIntroBo bo);

    /**
     * 批量引种（≥ 2 头）。
     *
     * <p>批量耳号由 {@code IBizCodeGenerator#generateBatch(EAR_NO, ctx, count)} 一次性分配 N 个连续序号，
     * 避免循环逐头分配造成的锁竞争。</p>
     *
     * @param bo 批量入参（pigCount + startEarNo + 性别统一）
     * @return 引种业务行 + 创建的猪只摘要（pigs.size==count）
     */
    PigIntroResultVo introduceBatch(PigIntroBatchBo bo);

    /**
     * 分页查询引种历史（admin 只读列表）。
     *
     * @param query     过滤条件（按 introduceNo / type / supplier / date 范围）
     * @param pageQuery 分页 + 排序
     * @return 分页结果
     */
    TableDataInfo<PigIntroduceVo> queryPage(PigIntroQuery query, PageQuery pageQuery);
}
