package com.fundpilot.backend.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Instant ↔ SQL DATE 转换器(ADR:全局 Instant)。
 * <p>Java 代码层统一用 {@link Instant},SQL 列保持 DATE 类型(不改 schema)。
 * 写库:Instant → UTC 当日 LocalDate;读库:LocalDate → UTC 0 点 Instant。
 * <p>应用到 Entity 字段时用 {@code @Convert(converter = InstantDateConverter.class)}。
 */
@Converter(autoApply = false)
public class InstantDateConverter implements AttributeConverter<Instant, LocalDate> {

    @Override
    public LocalDate convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : attribute.atZone(ZoneOffset.UTC).toLocalDate();
    }

    @Override
    public Instant convertToEntityAttribute(LocalDate dbData) {
        return dbData == null ? null : dbData.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
