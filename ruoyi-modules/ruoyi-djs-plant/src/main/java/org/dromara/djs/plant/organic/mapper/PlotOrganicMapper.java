package org.dromara.djs.plant.organic.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.organic.domain.PlotOrganic;
import org.dromara.djs.plant.organic.domain.vo.PlotOrganicVo;

/**
 * 土地有机证书 Mapper（PLT-MD-003）。
 *
 * @author djs
 * @since PLT-MD-003
 */
public interface PlotOrganicMapper extends BaseMapperPlus<PlotOrganic, PlotOrganicVo> {

    /**
     * 扫描到期日 ≤ N 天且当前未预警的证书，置 {@code is_warning=1}。
     *
     * <p>由 {@code OrganicWarningJob} 每天 0 点调用；同事务下与作物证书 scan 一起。</p>
     *
     * @param days 阈值天数（默认 60，来自 sys_config plant.organic.warning_days）
     * @return 实际更新行数
     */
    @Update("UPDATE t_plant_plot_organic SET is_warning=1, update_time=NOW() "
        + "WHERE del_flag='0' AND is_warning=2 "
        + "AND DATEDIFF(organic_valid, CURRENT_DATE) <= #{days}")
    int markWarning(@Param("days") int days);
}
