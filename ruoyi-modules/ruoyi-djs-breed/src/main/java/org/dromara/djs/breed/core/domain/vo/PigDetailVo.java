package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * 猪只详情出参（BRD-CORE-001）。
 *
 * <p>= {@link PigVo} 主体 + {@code recentHistory}（最近 N 条状态变更，默认 20）。</p>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PigDetailVo extends PigVo {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 最近状态变更记录（默认按 change_time DESC 截前 20 条）。 */
    private List<PigStatusRecordVo> recentHistory;

    /**
     * 猪只照片「可访问 URL」（详情顶卡主图，mp `image` 优先于生长记录图）。
     * <p>V1 取引种登记凭证图首张（{@code t_farm_pig_introduce.proof_oss_ids} 按 pig_id / start_ear_no 反查）；
     * 无引种照片时前端回退 {@link #growthPhotoOssId}。mp {@code <image>} 带不了 Bearer token，故后端预解析成 URL。</p>
     */
    private String image;

    /**
     * 该猪最新生长记录主图「可访问 URL」（{@code t_farm_pig_growth} 按 measure_date DESC 取首条的 photo_oss_ids 第一张）。
     * <p>详情顶卡 {@link #image} 缺时回退用此图；无生长记录 / 该记录无照片 → null。</p>
     */
    private String growthPhotoOssId;
}
