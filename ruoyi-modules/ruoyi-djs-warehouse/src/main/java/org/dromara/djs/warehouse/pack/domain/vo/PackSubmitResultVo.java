package org.dromara.djs.warehouse.pack.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 打包提交结果（DJS-FIX-WMS-PACK：「确认并打印追溯码」用）。
 *
 * <p>admin 端肉品 / 果蔬打包 submit 后返回新建 production 的 {@code id} + {@code traceCode}，
 * 供前端「确认并打印追溯码」按钮展示/打印追溯码。traceCode 由 service 端
 * {@code fillTraceCode → TraceService.genCode} 同事务生成回填（TRC-CORE-001）。</p>
 *
 * @author djs
 * @since DJS-FIX-WMS-PACK
 */
@Data
public class PackSubmitResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 新建生产记录 ID。 */
    private Long id;

    /** 追溯码（打包入库时生成；猪肉链当前无生成入口时可能为 null）。 */
    private String traceCode;

    /** 生产编号（{@code produce_no} 业务码，yyMMdd + 前缀 + 4 位序号）：追溯码打印弹框「生产序号」展示。 */
    private String produceNo;

    public PackSubmitResultVo() {
    }

    public PackSubmitResultVo(Long id, String traceCode, String produceNo) {
        this.id = id;
        this.traceCode = traceCode;
        this.produceNo = produceNo;
    }
}
