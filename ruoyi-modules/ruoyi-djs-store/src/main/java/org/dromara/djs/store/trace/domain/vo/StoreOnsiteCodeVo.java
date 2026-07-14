package org.dromara.djs.store.trace.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店现场生码返回 VO（测试问题 row84）。
 *
 * <p>门店猪肉打包现场生码返回两个码：</p>
 * <ul>
 *   <li>{@code produceCode} —— 追溯码（{@code T{yyyyMMdd}PG{seq6}}），二维码 encode 用（C 端扫码走
 *       {@code /trace/pork/{code}}），不可改格式否则追溯查不到。</li>
 *   <li>{@code productionCode} —— 门店打包生产编码（{@code <生产标识码>YYMMDD####}），追溯标签「生产编码」
 *       文本展示用（按门店生产标识码 + 每日重置流水）。</li>
 * </ul>
 *
 * @author djs
 * @since DENGBO-ROW84
 */
@Data
public class StoreOnsiteCodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 追溯码 produce_code（二维码 encode 用，格式 {@code T{yyyyMMdd}PG{seq6}}）。 */
    private String produceCode;

    /** 门店打包生产编码（追溯标签「生产编码」文本展示，格式 {@code <生产标识码>YYMMDD####}）。 */
    private String productionCode;
}
