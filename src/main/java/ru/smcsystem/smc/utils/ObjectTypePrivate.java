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
            case STRING_NULL:
                return ObjectType.STRING;
            case BYTE:
            case BYTE_NULL:
                return ObjectType.BYTE;
            case SHORT:
            case SHORT_NULL:
                return ObjectType.SHORT;
            case INTEGER:
            case INTEGER_NULL:
                return ObjectType.INTEGER;
            case LONG:
            case LONG_NULL:
                return ObjectType.LONG;
            case FLOAT:
            case FLOAT_NULL:
                return ObjectType.FLOAT;
            case DOUBLE:
            case DOUBLE_NULL:
                return ObjectType.DOUBLE;
            case BIG_INTEGER:
            case BIG_INTEGER_NULL:
                return ObjectType.BIG_INTEGER;
            case BIG_DECIMAL:
            case BIG_DECIMAL_NULL:
                return ObjectType.BIG_DECIMAL;
            case BYTES:
            case BYTES_NULL:
                return ObjectType.BYTES;
            case OBJECT_ELEMENT_OPTIMIZED:
            case OBJECT_ELEMENT_SIMPLE_OPTIMIZED:
                return ObjectType.OBJECT_ELEMENT;
            case BOOLEAN:
            case BOOLEAN_NULL:
                return ObjectType.BOOLEAN;
        }
        throw new IllegalArgumentException(this.name());
    }

}
