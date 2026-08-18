package com.talon.core.users.internal.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GroupConverter implements AttributeConverter<Group, String> {
    @Override
    public String convertToDatabaseColumn(Group group) {
        return group == null ? null : group.getValue();
    }

    @Override
    public Group convertToEntityAttribute(String value) {
        return value == null ? null : Group.fromValue(value);
    }
}
