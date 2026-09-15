package org.dromara.djs.store.returns.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店退回操作页「退回门店」筛选项（V6 row220 系列 row222）。
 *
 * <p>取值来源是<b>列表自己已有的退回记录</b>去重，不是全量门店档案：甲方原话
 * 「这里的退回门店数据取列表里的退回门店数据去重后的数据内容」。全量门店档案里绝大多数门店
 * 从没退过货，摆在下拉里选一个查出空页，而真正需要筛的「单位退回」那些单位名反倒一个都没有
 * （它们 {@code store_id} 恒 NULL，根本不在门店档案里）。</p>
 *
 * <p>一条选项要么是门店（{@code returnType=store}，带 {@code storeId}），要么是退回单位
 * （{@code returnType=unit}，带 {@code returnUnit} 字典 value），二者互斥。前端把它拼成
 * 单一下拉的 value（{@code store:<id>} / {@code unit:<value>}），选中后各自下推到
 * {@code storeId} / {@code returnUnit} 两个查询条件。</p>

 * <p>⚠️ 别指望「单位退回的 {@code store_id} 恒 NULL」——历史脏数据里有反例
 * （{@code RET202609150001}：{@code return_type='unit'} 却带着 {@code store_id}）。
 * 按门店筛时靠 {@code buildQueryWrapper} 里那道显式的 {@code return_type <> 'unit'} 排除，不靠数据恰好为空。</p>
 *
 * @author djs
 */
@Data
public class StoreReturnOwnerOptionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 退回类型（store=门店退回 / unit=单位退回）。 */
    private String returnType;

    /** 门店 id（{@code returnType=store} 时有值）。 */
    private Long storeId;

    /** 退回单位字典 value（{@code returnType=unit} 时有值）。 */
    private String returnUnit;

    /**
     * 列表「退回门店」列上显示的那个名字。
     *
     * <p>单位取字典 {@code djs_return_unit} 的 label，查不到 label 时退回裸 value ——
     * 与列表列同一套取名规则（{@code unitLabel}），否则下拉里写「叶家庄村」、
     * 列表里显示 {@code yejiazhuang_cun}，用户对不上。</p>
     *
     * <p>门店取门店档案名；<b>门店被软删导致查不到名字时，这里退回 id 字符串而列表列显示「—」</b>，
     * 两处不一致是有意的：下拉项必须有个能看能选的标识，退成「—」会出现一个选了等于没选的空项。
     * 触发条件是「有退回记录的门店被软删」，当前数据没有这种行。</p>
     */
    private String label;
}
