package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 猪只详情「用药记录」表行出参（DJS-FIX-6-14 row212 / row213，原型 用药记录 tab）。
 *
 * <p>来源 {@code t_breed_medicine_record}（按 pig_id，drug_type 1 单只 / 3 批量 detail 都含），
 * 倒序 by use_date。原因 / 方式字典已在 service 层翻成中文 label（mp 无 i18n 字典翻译能力）。</p>
 *
 * @author djs
 * @since DJS-FIX-6-14
 */
@Data
public class PigMedRecordMpVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用药日期（'YYYY-MM-DD'）。 */
    private String useDate;

    /** 用药原因 label（已字典翻译，djs_medicine_reason）；缺 → null。 */
    private String reason;

    /** 药品处方 = 药品名 medicine_name。 */
    private String medicineName;

    /** 用药量 medicine_dosage。 */
    private BigDecimal dosage;

    /** 用药方式 label（已字典翻译，djs_medicine_way）。 */
    private String way;

    /** 用药人 operator_name。 */
    private String operatorName;
}
