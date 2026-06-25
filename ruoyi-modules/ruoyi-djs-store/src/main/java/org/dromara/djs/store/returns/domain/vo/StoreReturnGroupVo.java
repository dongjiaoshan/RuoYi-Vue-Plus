package org.dromara.djs.store.returns.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 门店退回（门店→仓库）mp 门店分组聚合 VO（{@code GET /applet/store/return/pending}，STORE-RETURN-UNIFY-001）。
 *
 * <p>对齐原型「退货管理」分组卡：每张卡 = 一个门店当天的全部退回行（状态为派生值）：</p>
 * <ul>
 *   <li>门店名 + 派生状态：该门店当天退回全部 received → {@code confirmed}（mp 词表），否则 {@code pending}</li>
 *   <li>退回产品品种数（该门店当天全部行 distinct product_id 计数）</li>
 *   <li>退回时间（该门店当天退回中最大 return_date）</li>
 * </ul>
 *
 * <p>字段名与 mp {@code ReturnStoreGroup} 对齐（returnStatus 用 mp 词表 pending/confirmed），mp api 仅换路径。</p>
 *
 * @author djs
 * @since STORE-RETURN-UNIFY-001
 */
@Data
public class StoreReturnGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 门店 ID（snowflake，Jackson 序列化为 string）。 */
    private Long storeId;

    /** 门店名称（StoreMapper 批量填充，避免 N+1）。 */
    private String storeName;

    /** 退回状态（mp 词表 djs_return_status：pending / confirmed；由 store 的 pending/received 派生映射）。 */
    private String returnStatus;

    /** 退回产品品种数（该门店当天 distinct product_id 计数）。 */
    private int productKindCount;

    /** 退回时间（该组最近一条 return_date，列表展示用）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime returnTime;
}
