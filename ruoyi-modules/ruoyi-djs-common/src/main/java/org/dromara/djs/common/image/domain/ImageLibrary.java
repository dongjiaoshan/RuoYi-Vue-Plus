package org.dromara.djs.common.image.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 公共图片库实体（IMG-LIB-001）。
 *
 * <p>对应表 {@code t_md_image_library}。作物 / 商品主数据 create 时按 name 调
 * {@link org.dromara.djs.common.image.service.IImageLibraryService#match} 自动匹配出 {@code oss_id}
 * 存入主数据的 {@code image_oss_id} 字段（write-time，不是 read-time 解析）。</p>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}（{@code DjsMetaObjectHandler} 在
 * delFlag='1' 时把 id 写入 delUnique，保证 UNIQUE(tenant_id, image_name, del_unique) 不阻塞同名复用）。</p>
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_md_image_library")
public class ImageLibrary extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 主名（与作物 cropName / 商品 productName 精确匹配）。
     */
    private String imageName;

    /**
     * 别名（逗号分隔，如 {@code 西红柿,tomato}；匹配时 contains 命中）。
     */
    private String aliases;

    /**
     * 单张图 ossId（{@code sys_oss.oss_id}，字符串）。
     */
    private String ossId;

    /**
     * 排序。
     */
    private Integer sortOrder;

    /**
     * 状态（字典 {@code sys_normal_disable}：0 正常 / 1 停用；停用的不参与 match）。
     */
    private String status;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
