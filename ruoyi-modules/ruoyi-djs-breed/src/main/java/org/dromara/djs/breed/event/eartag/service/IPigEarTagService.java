package org.dromara.djs.breed.event.eartag.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBatchEarTagBo;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBirthWeightBo;
import org.dromara.djs.breed.event.eartag.domain.query.PigletEarTagQuery;
import org.dromara.djs.breed.event.eartag.domain.vo.EarNoPreviewVo;
import org.dromara.djs.breed.event.eartag.domain.vo.FarrowEarTagStatVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletnoVo;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;

import java.util.List;

/**
 * 仔猪批量耳标 Service（BRD-EVENT-003）。
 *
 * <p><b>设计要点</b>：</p>
 * <ul>
 *   <li>仔猪 <b>不参与状态机</b> — 不调 {@code IPigCoreService#fireEvent} 也不调 {@code createPig}，
 *       而是同事务 INSERT {@code t_farm_pig_info}（pig_type='piglet', current_status='HB' 默认）
 *       + INSERT {@code t_farm_pig_pigletno}（耳标日志）。</li>
 *   <li>耳号由 SYS-INFRA-004 {@code generateBatch} 一次连续生成 N 个（Redisson 分布式锁保唯一）。</li>
 *   <li>已贴头数 + 本批头数 ≤ {@code farrow.live_born} 强制校验。</li>
 *   <li>父猪耳号从 {@code farrow.breeding_id → t_farm_pig_breeding.boar_ear_no} 反查，可能为空。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
public interface IPigEarTagService {

    /**
     * 统计某次分娩的耳标进度（活产 / 已贴 / 待贴 + 已贴清单）。
     *
     * @param farrowId 分娩记录 ID
     * @return 统计 VO；farrow 不存在抛 ServiceException
     */
    FarrowEarTagStatVo statByFarrow(Long farrowId);

    /**
     * 批量贴耳标：一次 transaction 内为 N 头仔猪创建 pig_info + pigletno。
     *
     * @param bo 入参（含 farrowId + N 个 PigletEarTagItem）
     * @return 生成的 N 个 VO（耳号、pig_id、出生重等）
     */
    List<PigletEarTagVo> batchTag(PigletBatchEarTagBo bo);

    /**
     * 按窝自动建档整窝仔猪（V6 行242：分娩录入提交即建档，工人不再单独确认耳号）。
     *
     * <p>头数取窝级公母数 {@code farrow.male_count / female_count}（由分娩录入的
     * 健仔公+弱仔留养公 / 健仔母+弱仔留养母 派生落库）；两者都为 0 时整窝按公组铺
     * {@code live_born} 头，与 mp 耳标页原预铺行的退化规则一致。</p>
     *
     * <p>每头出生重取字典 {@code djs_piglet_default_weight} 的「仔猪出生重」当前值
     * （取不到回落 2kg）—— <b>依据 D-0107</b>：留空会让窝均初生重先显示 0 再跳变，
     * 工人会以为数据丢了，故写默认值，由出生重订正页（{@link #adjustBirthWeights}）改准。</p>
     *
     * <p>{@code live_born} 为 0 / 空时不建档也不报错（返空 list）。其余失败一律抛异常，
     * 由调用方事务整体回滚——不得吞。</p>
     *
     * @param farrow     已落库的分娩记录（调用方刚 INSERT 的那条）
     * @param operatorId 记到 pigletno.operator_id 的人员（空则回落当前登录态）
     * @return 建档的 N 头 VO；不建档时返空 list
     */
    List<PigletEarTagVo> autoCreatePigletsForFarrow(PigFarrow farrow, Long operatorId);

    /**
     * 进入出生重订正页时读本窝仔猪清单——<b>一头都没有就先补建整窝</b>（D-0110）。
     *
     * <p>行242 上线前已分娩、从未打标的老窝进来时是空的，没有耳号可订正，页面会是一张空表单。
     * 故读的时候就按 {@link #autoCreatePigletsForFarrow} 同一套规则补建，与
     * {@link #adjustBirthWeights} 提交时的补建是同一条路径、同一套规则，不写两套分支。</p>
     *
     * <p><b>幂等</b>：已有档案时纯读，不重复建。</p>
     *
     * @param farrowId 分娩记录 ID
     * @return 本窝统计 + 仔猪清单（耳号 / 性别 / 当前出生重），与 {@link #statByFarrow} 同结构
     */
    FarrowEarTagStatVo ensureLitterCreated(Long farrowId);

    /**
     * 订正本窝仔猪出生重（V6 行243：确认提交只改重量，不再打标、不再选人）。
     *
     * <p>两张表同改——{@code t_farm_pig_info.birth_weight} 与
     * {@code t_farm_pig_pigletno.birth_weight} 存的是同一个事实（既有的重复存储），
     * 只改一边会让按 pigletno 汇总出来的窝级总重与主表对不上。收尾回写
     * {@code t_farm_pig_farrow.total_weight / avg_weight}。</p>
     *
     * <p>本窝一头都没建档时（行242 上线前已分娩、从未打标的老窝）先按
     * {@link #autoCreatePigletsForFarrow} 同一套规则补建整窝再订正 —— <b>依据 D-0110</b>：
     * 「档案不存在就补建，存在就只改重量」，新老窝同一条路径，不写两套分支。</p>
     *
     * <p><b>幂等</b>：出生重是绝对赋值 + 窝级重量每次重算，同一窝反复提交结果一致。
     * 传入耳号不属于本窝 → 抛 ServiceException（不静默跳过）。</p>
     *
     * @param bo 入参（farrowId + 耳号/出生重列表）
     * @return 订正后本窝全部仔猪 VO（按耳号 asc）
     */
    List<PigletEarTagVo> adjustBirthWeights(PigletBirthWeightBo bo);

    /**
     * 预览某次分娩下一批公/母连号耳号（K122，仅预览不占号）。
     *
     * <p>按 farrowId 反查母猪品系/品种 + 分娩日，分别为公组/母组按 {@code DB max 同前缀 + 1} 推算 N 个
     * 连号全号，供录入行预填「预计耳号」。真实分配仍在 {@link #batchTag} 提交时锁内做，并发下预览号可能
     * 与最终号有偏差，前端以提交回显为准。</p>
     *
     * @param farrowId    分娩记录 ID
     * @param maleCount   公组预览头数（&lt; 0 视为 0）
     * @param femaleCount 母组预览头数（&lt; 0 视为 0）
     * @return 公/母两组预计耳号串
     */
    EarNoPreviewVo previewEarNos(Long farrowId, int maleCount, int femaleCount);

    /**
     * 分页查询仔猪耳标历史（admin 只读列表）。
     */
    TableDataInfo<PigletnoVo> queryPage(PigletEarTagQuery query, PageQuery pageQuery);
}
