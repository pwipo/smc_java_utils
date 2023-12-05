package ru.smcsystem.smc.utils.converter;

import ru.smcsystem.api.dto.ObjectElement;
import ru.smcsystem.api.dto.ObjectField;

public abstract class SmcConverter<T> {
    /**
     * Convert a field to a value.
     *
     * @param field         - The field to be converted. Can be null.
     * @param objectElement - full input ObjectElement
     * @throws Exception - if there's an error during the conversion.
     */
    public abstract T to(ObjectField field, ObjectElement objectElement) throws Exception;

    /**
     * Convert a value to a field.
     *
     * @param name          - The name of field
     * @param v             - The value to be converted. Can be null.
     * @param objectElement - result ObjectElement
     * @throws Exception - if there's an error during the conversion.
     */
    public abstract ObjectField from(String name, T v, ObjectElement objectElement) throws Exception;

    public abstract static class None
            extends SmcConverter<Object> {
    }

}
