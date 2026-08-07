package org.dromara.djs.breed.core.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * admin 端「修改耳号」入参（BRD-LIST-EDIT-001）。
 *
 * <p>小程序引种/打耳标时手误录错序号是常见场景（如 {@code 01-01-2-251031-031}
 * 想改成 {@code 01-01-2-251031-030}），此前只能找开发手工改库。本 BO 支撑一个窄口
 * 编辑端点，只改耳号本身，不碰其他字段。</p>
 *
 * <p>格式与 {@link org.dromara.djs.breed.core.service.EarNoAllocator} 一致：
 * {@code 品系(1-2位)-品种2位-性别1位(可选)-出生日yyMMdd6位-序号3位}，正则同
 * {@code PigIntroServiceImpl.allocateFromUserStart}，保持项目内耳号格式校验单一口径。</p>
 *
 * @author djs
 * @since BRD-LIST-EDIT-001
 */
@Data
public class PigEarNoUpdateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 新耳号（全版，含分隔符）。 */
    @NotBlank(message = "{pig.ear_no.update_required}")
    @Pattern(regexp = "^\\d{1,2}-\\d{2}(-\\d)?-\\d{6}-\\d{3}$", message = "{pig.ear_no.update_pattern}")
    private String earNo;

    /** 乐观锁版本（前端回传编辑前拿到的 version，防并发覆盖）。 */
    @NotNull(message = "{pig.ear_no.update_version_required}")
    private Integer version;
}
