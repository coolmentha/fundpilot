package com.fundpilot.backend.discipline.domain.classification;

/** 最终分类由 Discipline 所有，目录分类只可作为默认建议。 */
public record DisciplineClassification(long portfolioFundId, long ownerId, DisciplineCategory category,
                                       DisciplineClassificationSource source) {
}
