package org.dromara.djs.plant.organic.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.organic.domain.CropOrganic;
import org.dromara.djs.plant.organic.domain.vo.CropOrganicVo;

/**
 * 果蔬有机证书 Mapper（PLT-MD-003）。
 *
 * @author djs
 * @since PLT-MD-003
 */
public interface CropOrganicMapper extends BaseMapperPlus<CropOrganic, CropOrganicVo> {

    /**
     * 扫描到期日 ≤ N 天且当前未预警的证书，置 {@code is_warning=1}。
     */
    @Update("UPDATE t_plant_crop_organic SET is_warning=1, update_time=NOW() "
        + "WHERE del_flag='0' AND is_warning=2 "
        + "AND DATEDIFF(crop_cert_valid, CURRENT_DATE) <= #{days}")
    int markWarning(@Param("days") int days);
}
