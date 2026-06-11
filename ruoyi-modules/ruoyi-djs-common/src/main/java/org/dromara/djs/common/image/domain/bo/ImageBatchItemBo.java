package org.dromara.djs.common.image.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 批量导入单项入参 BO（IMG-LIB-001 批量上传 + 按文件名自动归档）。
 *
 * <p>admin 把一批已命名的图（如 {@code 番茄.jpg}）一次拖上传：每张走 OSS STS 直传拿 ossId，
 * 前端用文件名去扩展名当 {@code imageName} 配对，整批 POST 到 batchImport。</p>
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Data
public class ImageBatchItemBo {

    /**
     * 主名（文件名去扩展名，如 {@code 番茄}）。与作物 cropName / 商品 productName 精确匹配。
     */
    @NotBlank(message = "图片主名不能为空")
    @Size(max = 64, message = "图片主名长度不能超过 {max} 个字符")
    private String imageName;

    /**
     * 单张图 ossId（雪花字符串，全链路 string 禁 Number）。
     */
    @NotBlank(message = "ossId 不能为空")
    @Size(max = 32, message = "ossId 长度非法")
    private String ossId;

}
