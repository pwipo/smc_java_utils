package ru.smcsystem.smc.utils.converter;

import ru.smcsystem.api.dto.ObjectElement;
import ru.smcsystem.api.dto.ObjectField;
import ru.smcsystem.smc.utils.ModuleUtils;

import java.time.Instant;

public class SmcConverterInstant extends SmcConverter<Instant> {

    @Override
    public Instant to(ObjectField field, ObjectElement objectElement) throws Exception {
        return Instant.ofEpochMilli(ModuleUtils.toNumber(field).longValue());
    }

    @Override
    public ObjectField from(String name, Instant v, ObjectElement objectElement) throws Exception {
        return new ObjectField(name, v.toEpochMilli());
    }
}
