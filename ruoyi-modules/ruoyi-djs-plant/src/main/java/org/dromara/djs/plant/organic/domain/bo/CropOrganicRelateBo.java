package org.dromara.djs.plant.organic.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 果蔬证书「关联作物」轻量入参 BO（FIX-PLT-AD-INFO-LIST-001）。
 *
 * <p>列表行操作「关联作物」el-transfer 弹窗专用：只提交证书 id + 作物 id 列表，
 * 不重提整张证书表单。service 层先软删旧关联再插入新关联（同事务）。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-INFO-LIST-001
 */
@Data
public class CropOrganicRelateBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 证书 ID。 */
    @NotNull(message = "{plant.organic.crop_required}")
    private Long id;

    /** 关联作物 ID 列表（可空 = 清空全部关联）。 */
    private List<Long> cropIds;
}
