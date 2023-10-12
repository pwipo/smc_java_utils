package ru.smcsystem.smc.utils;

import ru.smcsystem.api.enumeration.ObjectType;

public enum ObjectTypePrivate {
    OBJECT_ARRAY,
    OBJECT_ELEMENT,
    OBJECT_ELEMENT_SIMPLE,
    VALUE_ANY,
    STRING,
    BYTE,
    SHORT,
    INTEGER,
    LONG,
    FLOAT,
    DOUBLE,
    BIG_INTEGER,
    BIG_DECIMAL,
    BYTES,
    OBJECT_ELEMENT_OPTIMIZED,
    OBJECT_ELEMENT_SIMPLE_OPTIMIZED,
    BOOLEAN,
    STRING_NULL,
    BYTE_NULL,
    SHORT_NULL,
    INTEGER_NULL,
    LONG_NULL,
    FLOAT_NULL,
    DOUBLE_NULL,
    BIG_INTEGER_NULL,
    BIG_DECIMAL_NULL,
    BYTES_NULL,
    BOOLEAN_NULL;

    ObjectType convert() {
        switch (this) {
            case OBJECT_ARRAY:
                return ObjectType.OBJECT_ARRAY;
            case OBJECT_ELEMENT:
            case OBJECT_ELEMENT_SIMPLE:
                return ObjectType.OBJECT_ELEMENT;
            case VALUE_ANY:
                return ObjectType.VALUE_ANY;
            case STRING:
                return ObjectType.STRING;
            case BYTE:
                return ObjectType.BYTE;
            case SHORT:
                return ObjectType.SHORT;
            case INTEGER:
                return ObjectType.INTEGER;
            case LONG:
                return ObjectType.LONG;
            case FLOAT:
                return ObjectType.FLOAT;
            case DOUBLE:
                return ObjectType.DOUBLE;
            case BIG_INTEGER:
                return ObjectType.BIG_INTEGER;
            case BIG_DECIMAL:
                return ObjectType.BIG_DECIMAL;
            case BYTES:
                return ObjectType.BYTES;
            case OBJECT_ELEMENT_OPTIMIZED:
            case OBJECT_ELEMENT_SIMPLE_OPTIMIZED:
                return ObjectType.OBJECT_ELEMENT;
            case BOOLEAN:
                return ObjectType.BOOLEAN;
        }
        throw new IllegalArgumentException(this.name());
    }

}
