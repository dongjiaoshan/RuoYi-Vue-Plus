package org.dromara.djs.breed.farm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 栋舍 picker 轻量 VO（D9 closing Group D — BarnPicker）。
 *
 * <p>专给 mp 端 {@code BarnPicker} 用。工人在转移 / 引种事件需要选目标栋舍编码（业务字符串如 'B01'），
 * picker 直接展示 barnCode + barnName + capacity / used。</p>
 *
 * <p>跨层契约：栋舍编码是业务码 string（不是 snowflake），mp emit barnCode 给业务 BO。</p>
 *
 * @author djs
 * @since D9-closing Group D
 */
@Data
public class BarnPickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 栋舍主键（snowflake string，list key 用，业务 emit barnCode 而非 id）。 */
    private Long id;

    /** 栋舍编码（业务码，picker emit 值）。 */
    private String barnCode;

    /** 栋舍名称（picker 主展示）。 */
    private String barnName;

    /** 栋舍类型（字典 djs_barn_type，picker tag 展示）。 */
    private String barnType;

    /** 容量。 */
    private Integer capacity;

    /** 当前存栏（picker 副标识"5/20"）。 */
    private Integer usedCount;
}
