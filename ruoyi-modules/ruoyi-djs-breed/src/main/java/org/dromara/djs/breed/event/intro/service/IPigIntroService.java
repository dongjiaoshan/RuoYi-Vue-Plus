package org.dromara.djs.breed.event.intro.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBatchBo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroInternalBo;
import org.dromara.djs.breed.event.intro.domain.query.PigIntroQuery;
import org.dromara.djs.breed.event.intro.domain.vo.IntroRecordVo;
import org.dromara.djs.breed.event.intro.domain.vo.PigIntroResultVo;
import org.dromara.djs.breed.event.intro.domain.vo.PigIntroduceVo;

import java.time.LocalDate;

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
     * 内部引种（BRD-FIX-MP-INTRO-001）：登记一头**已存在**的猪进引种台账，**不新建猪**。
     *
     * <p>只写一行 {@code t_farm_pig_introduce}（introduce_type=internal + pig_id 关联），
     * 不调 {@code createPig}、不触发状态机、不改 pen.current_count（猪已在库里）。</p>
     *
     * @param bo 内部引种入参（pigId + introduceDate + introduceWeight + operator）
     * @return 引种业务行 + 关联猪只摘要（pigs.size==1）
     */
    PigIntroResultVo introduceInternal(PigIntroInternalBo bo);

    /**
     * mp 端「引种记录」列表（BRD-FIX-MP-INTRO-001，原型 86 第 3 段）。
     *
     * <p>按 introduceDate DESC，返轻量 {@link IntroRecordVo}（含引种方式 label + 耳号 + 品种 label）。</p>
     *
     * @param query     过滤条件（与 admin list 共用）
     * @param pageQuery 分页
     * @return 分页结果
     */
    TableDataInfo<IntroRecordVo> queryAppletRecords(PigIntroQuery query, PageQuery pageQuery);

    /**
     * 分页查询引种历史（admin 只读列表）。
     *
     * @param query     过滤条件（按 introduceNo / type / supplier / date 范围）
     * @param pageQuery 分页 + 排序
     * @return 分页结果
     */
    TableDataInfo<PigIntroduceVo> queryPage(PigIntroQuery query, PageQuery pageQuery);

    /**
     * 预生成"下一个可用首耳号"（外部引种可改耳号预填用，601-5 / ADR-0011 §2.6）。
     *
     * <p>按品系 + 品种 + 公母 + 出生日组装带分隔符前缀（品系1-品种2-公母1-yyMMdd6，如 1-01-1-260609），取当天级 max+1 拼出首号返给前端预填，<b>不落库</b>；
     * 用户改后提交时再由 {@code introduceBatch} 校 UNIQUE，并发由 {@code EarNoAllocator} 锁 + 兜底重算。</p>
     *
     * @param strainCode 品系码（{@code djs_pig_strain} dict_value，1 位）
     * @param breedCode  品种码（{@code djs_pig_breed} dict_value，2 位）
     * @param pigSex     性别（M 公 / F 母）
     * @param birthDate  出生日期（空则用当天）
     * @return 预生成的带分隔符首耳号字符串（如 1-01-1-260609-0001）
     */
    String previewNextEarNo(String strainCode, String breedCode, String pigSex, LocalDate birthDate);

    /**
     * 耳号是否已被占用（外部引种 mp 端「输入即查重」用，甲方 V6 生产紧急表 row3）。
     *
     * <p>判重范围 = <b>同出生日 + 耳号后三位</b>（不分品系 / 品种 / 性别），整串耳号作兜底 ——
     * 甲方原话「判断输入的后三位在出生日期的判定里是否存在重复」。口径与取舍详见
     * {@code PigMapper#existsSeqByDateSegment}。与提交时 {@code introduceBatch} 的查重同一口径，
     * 保证「输入时说没重、提交却被拒」不会发生。</p>
     *
     * @param earNo 待校验的完整耳号（如 {@code 01-01-2-251031-206}）；格式不合法 → false（交提交时报格式错）
     * @return true = 已被占用
     */
    boolean isEarNoTaken(String earNo);
}
