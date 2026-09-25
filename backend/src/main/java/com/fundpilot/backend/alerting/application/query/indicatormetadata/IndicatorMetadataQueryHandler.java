package com.fundpilot.backend.alerting.application.query.indicatormetadata;

import com.fundpilot.backend.alerting.domain.condition.IndicatorCode;
import com.fundpilot.backend.alerting.domain.condition.IndicatorParameter;
import com.fundpilot.backend.alerting.domain.condition.IndicatorRelation;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 指标元数据读模型：前端条件构建器的唯一枚举来源。
 *
 * <p>指标、可选关系与参数范围都声明在 {@link IndicatorCode}，这里只把它们摊平成可序列化的形状，
 * 避免前端另写一份枚举导致「后端新增指标、前端不认」。
 */
@Service
public class IndicatorMetadataQueryHandler {

    public List<IndicatorMetadata> all() {
        return Arrays.stream(IndicatorCode.values()).map(IndicatorMetadata::from).toList();
    }

    public record IndicatorMetadata(String code, String label, String description, String source,
                                    BigDecimal minimum, BigDecimal maximum,
                                    List<ParameterMetadata> parameters, List<RelationMetadata> relations) {

        static IndicatorMetadata from(IndicatorCode indicator) {
            return new IndicatorMetadata(indicator.code(), indicator.label(), indicator.description(),
                    indicator.source().name(), indicator.minimum(), indicator.maximum(),
                    indicator.parameters().stream().map(ParameterMetadata::from).toList(),
                    indicator.relations().stream().map(RelationMetadata::from).toList());
        }
    }

    /** 参数定义：默认值与取值范围即为服务端的兜底与校验口径。 */
    public record ParameterMetadata(String name, String label, int defaultValue, int minimum, int maximum) {

        static ParameterMetadata from(IndicatorParameter parameter) {
            return new ParameterMetadata(parameter.name(), parameter.label(), parameter.defaultValue(),
                    parameter.minimum(), parameter.maximum());
        }
    }

    /**
     * 某个可选关系：{@code thresholded} 为假表示不参与阈值比较，前端不渲染阈值输入框；
     * 为真且 {@code defaultThreshold} 为空时用户必须填写阈值，否则可留空取默认值。
     */
    public record RelationMetadata(String relation, String label, boolean thresholded,
                                   BigDecimal defaultThreshold) {

        static RelationMetadata from(IndicatorRelation relation) {
            return new RelationMetadata(relation.relation().name(), relation.alias(),
                    relation.relation().thresholded(), relation.effectiveDefaultThreshold());
        }
    }
}