package com.mungroute.walk.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class WalkModeConverter implements AttributeConverter<WalkMode, String> {

    /**
     * 엔티티의 WalkMode를 DB에 저장할 문자열로 변환한다.
     */
    @Override
    public String convertToDatabaseColumn(WalkMode mode) {
        return mode == null ? null : mode.getValue();
    }

    /**
     * DB에서 조회한 문자열을 WalkMode로 변환한다.
     */
    @Override
    public WalkMode convertToEntityAttribute(String value) {
        return value == null ? null : WalkMode.from(value);
    }
}
