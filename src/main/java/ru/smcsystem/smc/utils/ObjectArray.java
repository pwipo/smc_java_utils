package ru.smcsystem.smc.utils;

import ru.smcsystem.api.dto.IValue;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * array of objects
 * use for serialization/deserialization messages as object - object serialization format
 * main class
 * contain list of object of same type.
 * the values can be one of the following types: ObjectElement, ObjectArray, String, Byte, Short, Integer, Long, Float, Double, BigInteger, BigDecimal, byte[].
 */
public class ObjectArray implements Serializable {

    private final List<Object> objects;

    private ObjectType type;

    public ObjectArray(int count, ObjectType type) {
        Objects.requireNonNull(type);
        this.objects = new ArrayList<>(count > 10 ? count + 1 : 10);
        this.type = type;
    }

    public ObjectArray(List<Object> objects, ObjectType type) {
        this(objects != null ? objects.size() : 0, type);
        if (objects != null)
            objects.forEach(this::add);
    }

    public ObjectArray() {
        this(null, ObjectType.OBJECT_ELEMENT);
    }

    private void check(Object value) {
        if (value == null)
            throw new IllegalArgumentException();
        switch (type) {
            case OBJECT_ARRAY:
                if (!(value instanceof ObjectArray))
                    throw new IllegalArgumentException();
                break;
            case OBJECT_ELEMENT:
                if (!(value instanceof ObjectElement))
                    throw new IllegalArgumentException();
                break;
            case OBJECT_ELEMENT_SIMPLE:
                if (!(value instanceof ObjectElement))
                    throw new IllegalArgumentException();
                if (!((ObjectElement) value).isSimple())
                    throw new IllegalArgumentException();
                break;
            case VALUE_ANY:
                break;
            case STRING:
                if (!(value instanceof String))
                    throw new IllegalArgumentException();
                break;
            case BYTE:
                if (!(value instanceof Byte))
                    throw new IllegalArgumentException();
                break;
            case SHORT:
                if (!(value instanceof Short))
                    throw new IllegalArgumentException();
                break;
            case INTEGER:
                if (!(value instanceof Integer))
                    throw new IllegalArgumentException();
                break;
            case LONG:
                if (!(value instanceof Long))
                    throw new IllegalArgumentException();
                break;
            case FLOAT:
                if (!(value instanceof Float))
                    throw new IllegalArgumentException();
                break;
            case DOUBLE:
                if (!(value instanceof Double))
                    throw new IllegalArgumentException();
                break;
            case BIG_INTEGER:
                if (!(value instanceof BigInteger))
                    throw new IllegalArgumentException();
                break;
            case BIG_DECIMAL:
                if (!(value instanceof BigDecimal))
                    throw new IllegalArgumentException();
                break;
            case BYTES:
                if (!(value instanceof byte[]))
                    throw new IllegalArgumentException();
                break;
        }
    }

    private void check(ObjectType type) {
        if (!this.type.equals(type) && !(ObjectType.OBJECT_ELEMENT.equals(this.type) && ObjectType.OBJECT_ELEMENT_SIMPLE.equals(type)))
            throw new IllegalArgumentException();
    }

    public int size() {
        return objects.size();
    }

    public Object get(int id) {
        return objects.get(id);
    }

    public void add(IValue value) {
        if (value == null)
            throw new IllegalArgumentException();
        check(ObjectType.valueOf(value.getType().name()));
        objects.add(value.getValue());
    }

    public void add(ObjectArray value) {
        if (value == null)
            throw new IllegalArgumentException();
        check(ObjectType.OBJECT_ARRAY);
        objects.add(value);
    }

    public void add(ObjectElement value) {
        if (value == null)
            throw new IllegalArgumentException();
        check(value.isSimple() ? ObjectType.OBJECT_ELEMENT_SIMPLE : ObjectType.OBJECT_ELEMENT);
        objects.add(value);
    }

    public void add(Object value) {
        check(value);
        objects.add(value);
    }

    public void add(int id, Object value) {
        check(value);
        objects.add(id, value);
    }

    public Object remove(int id) {
        return objects.remove(id);
    }

    public ObjectType getType() {
        return type;
    }

    public ObjectArray updateType() {
        if (ObjectType.OBJECT_ELEMENT.equals(getType())) {
            int countField = -1;
            boolean isSimple = true;
            for (int i = 0; i < objects.size(); i++) {
                ObjectElement objectElement = (ObjectElement) objects.get(i);
                if (i == 0)
                    countField = objectElement.getFields().size();
                if (countField != objectElement.getFields().size() || !objectElement.isSimple()) {
                    isSimple = false;
                    break;
                }
            }
            if (isSimple)
                this.type = ObjectType.OBJECT_ELEMENT_SIMPLE;
        } else if (ObjectType.VALUE_ANY.equals(getType())) {
            ObjectType newType = null;
            for (int i = 0; i < objects.size(); i++) {
                Object o = objects.get(i);
                if (o instanceof Number) {
                    if (o instanceof Byte) {
                        newType = checkType(newType, ObjectType.BYTE);
                    } else if (o instanceof Short) {
                        newType = checkType(newType, ObjectType.SHORT);
                    } else if (o instanceof Integer) {
                        newType = checkType(newType, ObjectType.INTEGER);
                    } else if (o instanceof Long) {
                        newType = checkType(newType, ObjectType.LONG);
                    } else if (o instanceof BigInteger) {
                        newType = checkType(newType, ObjectType.BIG_INTEGER);
                    } else if (o instanceof Float) {
                        newType = checkType(newType, ObjectType.FLOAT);
                    } else if (o instanceof Double) {
                        newType = checkType(newType, ObjectType.DOUBLE);
                    } else if (o instanceof BigDecimal) {
                        newType = checkType(newType, ObjectType.BIG_DECIMAL);
                    }
                } else if (o instanceof byte[]) {
                    newType = checkType(newType, ObjectType.BYTES);
                } else {
                    newType = checkType(newType, ObjectType.STRING);
                }
                if (newType == null)
                    break;
            }
            if (newType != null)
                this.type = newType;
        }
        return this;
    }

    private ObjectType checkType(ObjectType newType, ObjectType typeForCheck) {
        return newType != null && !typeForCheck.equals(newType) ? null : typeForCheck;
    }

}
