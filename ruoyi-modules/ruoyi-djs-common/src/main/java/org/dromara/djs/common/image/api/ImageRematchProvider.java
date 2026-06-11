package org.dromara.djs.common.image.api;

/**
 * 主数据图片批量重新匹配 provider（IMG-LIB-001 跨模块 facade）。
 *
 * <p>djs-common 不直接依赖 plant / warehouse 模块，无法直接更新 {@code t_plant_crop_info} /
 * {@code t_warehouse_product_info}。plant 与 warehouse 各注册一个实现，对自己域内
 * {@code image_source=0}（自动匹配，非手动）的主数据按 name 重跑
 * {@link org.dromara.djs.common.image.service.IImageLibraryService#match} 更新 {@code image_oss_id}。</p>
 *
 * <p>common 的 ImageController 用 {@code ObjectProvider<ImageRematchProvider>} 运行时收集所有实现并调用，
 * 零循环依赖（参考 {@code SupplierDealProvider} 模式）。上游模块未上线时集合为空，rematch 返 0。</p>
 *
 * @author djs
 * @since IMG-LIB-001
 */
public interface ImageRematchProvider {

    /**
     * 对本域所有 {@code image_source=0} 的主数据按 name 重新匹配图库，更新 {@code image_oss_id}。
     *
     * <p>覆盖三种场景：① seed 灌入后 image_oss_id 为空；② 图库换图（同名映射到新 ossId）；
     * ③ 新增图库行后回填此前匹配不上的记录。{@code image_source=1}（用户手改）的行不动。</p>
     *
     * @return 实际更新条数
     */
    int rematchAll();

    /**
     * 本域名称（日志 / 返回明细用，如 "crop" / "product"）。
     */
    String domainName();

}
