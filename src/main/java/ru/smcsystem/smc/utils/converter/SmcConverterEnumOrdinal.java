package ru.smcsystem.smc.utils.converter;

import ru.smcsystem.api.dto.ObjectElement;
import ru.smcsystem.api.dto.ObjectField;
import ru.smcsystem.api.enumeration.ObjectType;
import ru.smcsystem.smc.utils.ModuleUtils;

public class SmcConverterEnumOrdinal<T extends Enum<T>> extends SmcConverter<T> {
    private final Class<T> type;

    public SmcConverterEnumOrdinal(Class<T> type) {
        this.type = type;
    }

    @Override
    public T to(ObjectField field, ObjectElement objectElement) throws Exception {
        return type.getEnumConstants()[ModuleUtils.toNumber(field).intValue()];
    }

    @Override
    public ObjectField from(String name, T v, ObjectElement objectElement) throws Exception {
        return new ObjectField(name, ObjectType.INTEGER, v.ordinal());
    }
}
