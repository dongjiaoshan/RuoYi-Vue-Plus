package org.dromara.djs.plant.crop.service;

import org.dromara.djs.plant.crop.domain.bo.CropProductBo;
import org.dromara.djs.plant.crop.domain.vo.CropProductVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 作物关联产品配置 Service（V6 row16）。
 *
 * @author djs
 */
public interface ICropProductService {

    /** 某作物的产品配置列表（按 id 升序，首行 = 首选产品）。 */
    List<CropProductVo> listByCrop(Long cropId);

    /**
     * 批量取多个作物的产品配置（采摘明细 / 绩效结算 / mp 下拉共用，避免 N+1）。
     *
     * @return cropId → 配置行（按 id 升序）；无配置的作物不出现在 map 里
     */
    Map<Long, List<CropProductVo>> mapByCropIds(Collection<Long> cropIds);

    /**
     * 作物的主产品 = 产品配置首行（id 升序，产品已删的行不算）；没配返回 null。
     *
     * <p>作物→产品「取一个」的唯一口径（采摘默认产品、月台默认库位、领用原料、采摘活动等）。</p>
     */
    Long primaryProductId(Long cropId);

    /**
     * 反查：主产品是该产品的在用作物；没有返回 null。
     *
     * <p>一个产品可以出现在多个作物的配置里，但只会是至多一个作物的主产品，所以按主产品反查不会一对多。</p>
     */
    Long cropIdByPrimaryProduct(Long productId);

    int insertByBo(CropProductBo bo);

    int updateByBo(CropProductBo bo);

    int deleteByIds(Collection<Long> ids);
}
