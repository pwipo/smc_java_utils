package ru.smcsystem.smc.utils;

import ru.smcsystem.smc.utils.converter.SmcConverter;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

public class ObjectElementDescriptor<T> {
    private final String name;
    private final PropertyDescriptor propertyDescriptor;
    private final SmcField smcField;
    private final SmcConverter<?> smcConverter;

    public ObjectElementDescriptor(Class<T> cls, PropertyDescriptor propertyDescriptor) throws NoSuchFieldException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        this.propertyDescriptor = propertyDescriptor;
        this.smcField = cls.getDeclaredField(propertyDescriptor.getName()).getAnnotation(SmcField.class);
        this.name = smcField != null && !Objects.equals(smcField.name(), "##default") ? smcField.name() : propertyDescriptor.getName();
        this.smcConverter = smcField != null && smcField.converter() != SmcConverter.None.class ? smcField.converter().getConstructor().newInstance() : null;
    }

    public String getName() {
        return name;
    }

    public PropertyDescriptor getPropertyDescriptor() {
        return propertyDescriptor;
    }

    public SmcField getSmcField() {
        return smcField;
    }

    public SmcConverter<?> getSmcConverter() {
        return smcConverter;
    }

}
