package ru.smcsystem.smc.utils;

import ru.smcsystem.api.dto.IValue;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Field for Object
 * use for serialization/deserialization messages as object - object serialization format
 * the value can be one of the following types: ObjectArray, ObjectElement, String, Byte, Short, Integer, Long, Float, Double, BigInteger, BigDecimal, byte[].
 */
public class ObjectField implements Serializable {

    private String name;
    private Object value;
    private ObjectType type;

    public ObjectField(String name, IValue value) {
        this.setName(name);
        if (value != null)
            this.setValue(value);
    }

    public ObjectField(String name, ObjectArray value) {
        this.setName(name);
        if (value != null)
            this.setValue(value);
    }

    public ObjectField(String name, ObjectElement value) {
        this.setName(name);
        if (value != null)
            this.setValue(value);
    }

    public ObjectField(String name, Object value) {
        this.setName(name);
        if (value != null)
            this.setValue(value);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null)
            throw new IllegalArgumentException();
        this.name = name;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(IValue value) {
        if (value == null)
            throw new IllegalArgumentException();
        this.type = ObjectType.valueOf(value.getType().name());
        this.value = value.getValue();
    }

    public void setValue(ObjectArray value) {
        if (value == null)
            throw new IllegalArgumentException();
        this.type = ObjectType.OBJECT_ARRAY;
        this.value = value;
    }

    public void setValue(ObjectElement value) {
        if (value == null)
            throw new IllegalArgumentException();
        this.type = value.isSimple() ? ObjectType.OBJECT_ELEMENT_SIMPLE : ObjectType.OBJECT_ELEMENT;
        this.value = value;
    }

    public void setValue(Object value) {
        if (value == null)
            throw new IllegalArgumentException();
        if (value instanceof ObjectArray) {
            this.type = ObjectType.OBJECT_ARRAY;
        } else if (value instanceof ObjectElement) {
            this.type = ((ObjectElement) value).isSimple() ? ObjectType.OBJECT_ELEMENT_SIMPLE : ObjectType.OBJECT_ELEMENT;
        } else if (value instanceof IValue) {
            this.type = ObjectType.valueOf(((IValue) value).getType().name());
        } else if (value instanceof String) {
            this.type = ObjectType.STRING;
        } else if (value instanceof byte[]) {
            this.type = ObjectType.BYTES;
        } else if (value instanceof Number) {
            if (value instanceof Byte) {
                this.type = ObjectType.BYTE;
            } else if (value instanceof Short) {
                this.type = ObjectType.SHORT;
            } else if (value instanceof Integer) {
                this.type = ObjectType.INTEGER;
            } else if (value instanceof Long) {
                this.type = ObjectType.LONG;
            } else if (value instanceof Float) {
                this.type = ObjectType.FLOAT;
            } else if (value instanceof Double) {
                this.type = ObjectType.DOUBLE;
            } else if (value instanceof BigInteger) {
                this.type = ObjectType.BIG_INTEGER;
            } else if (value instanceof BigDecimal) {
                this.type = ObjectType.BIG_DECIMAL;
            } else {
                throw new IllegalArgumentException();
            }
        } else {
            throw new IllegalArgumentException();
        }
        this.value = value;
    }

    public ObjectType getType() {
        return type;
    }

    public boolean isSimple() {
        return ObjectType.OBJECT_ARRAY != type && ObjectType.OBJECT_ELEMENT != type && ObjectType.OBJECT_ELEMENT_SIMPLE != type;
    }

}
