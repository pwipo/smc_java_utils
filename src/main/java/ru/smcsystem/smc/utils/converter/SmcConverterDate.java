package ru.smcsystem.smc.utils.converter;

import ru.smcsystem.api.dto.ObjectElement;
import ru.smcsystem.api.dto.ObjectField;
import ru.smcsystem.smc.utils.ModuleUtils;

import java.util.Date;

public class SmcConverterDate extends SmcConverter<Date> {

    @Override
    public Date to(ObjectField field, ObjectElement objectElement) throws Exception {
        return new Date(ModuleUtils.toNumber(field).longValue());
    }

    @Override
    public ObjectField from(String name, Date v, ObjectElement objectElement) throws Exception {
        return new ObjectField(name, v.getTime());
    }
}
