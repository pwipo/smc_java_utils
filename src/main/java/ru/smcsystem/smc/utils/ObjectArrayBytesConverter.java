package ru.smcsystem.smc.utils;

import ru.smcsystem.api.dto.ObjectArray;
import ru.smcsystem.api.dto.ObjectElement;
import ru.smcsystem.api.dto.ObjectField;
import ru.smcsystem.api.enumeration.ObjectType;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

public class ObjectArrayBytesConverter {

    public static ObjectArray objectArrayFromByteArray(byte[] value) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(value); DataInputStream dis = new DataInputStream(bais)) {
            return objectArrayFromByteArray(dis);
        } catch (Exception ignore) {
        }
        return new ObjectArray();
    }

    public static byte[] toByteArray(ObjectArray value) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); DataOutputStream dos = new DataOutputStream(baos)) {
            toByteArray(value, dos);
            dos.flush();
            return baos.toByteArray();
        } catch (Exception ignore) {
        }
        return new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
    }

    public static void toByteArray(ObjectArray value, DataOutputStream dos) throws IOException {
        Objects.requireNonNull(value, "value not set");
        dos.writeInt(value.getType().ordinal());
        dos.writeInt(value.size());
        for (int i = 0; i < value.size(); i++) {
            Object o = value.get(i);
            ObjectType objectType = value.getType();
            if (objectType == ObjectType.VALUE_ANY) {
                objectType = ModuleUtils.convertTo(ModuleUtils.getValueTypeObject(o));
                dos.writeInt(objectType.ordinal());
            }
            toByteArray(objectType, o, dos);
        }
    }

    public static byte[] toByteArray(ObjectElement value) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); DataOutputStream dos = new DataOutputStream(baos)) {
            toByteArray(value, dos);
            dos.flush();
            return baos.toByteArray();
        } catch (Exception ignore) {
        }
        return new byte[]{0, 0, 0, 0};
    }

    private static void toByteArray(ObjectElement value, DataOutputStream dos) throws IOException {
        Objects.requireNonNull(value, "value not set");

        dos.writeInt(value.getFields().size());
        for (int i = 0; i < value.getFields().size(); i++) {
            ObjectField objectField = value.getFields().get(i);
            byte[] bytes = objectField.getName().getBytes();
            dos.writeInt(bytes.length);
            dos.write(bytes);
            if (objectField.getValue() != null) {
                dos.writeInt(objectField.getType().ordinal());
                toByteArray(objectField.getType(), objectField.getValue(), dos);
            } else {
                dos.writeInt(objectField.getType().ordinal() + 1000);
            }
        }
    }

    private static void toByteArray(ObjectType type, Object value, DataOutputStream dos) throws IOException {
        Objects.requireNonNull(type, "valueType not set");
        Objects.requireNonNull(value, "value not set");

        switch (type) {
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BOOLEAN:
                ValueBytesConverter.toByteArray(ModuleUtils.convertTo(type), value, dos);
                break;
            case BIG_INTEGER:
            case BIG_DECIMAL:
            case STRING:
            case BYTES: {
                byte[] bytes = ValueBytesConverter.toByteArray(ModuleUtils.convertTo(type), value);
                Objects.requireNonNull(bytes);
                dos.writeInt(bytes.length);
                dos.write(bytes);
                break;
            }
            case OBJECT_ELEMENT:
                toByteArray((ObjectElement) value, dos);
                break;
            case OBJECT_ARRAY:
                toByteArray((ObjectArray) value, dos);
                break;
            default:
                throw new RuntimeException("wrong type " + type);
        }
    }

    private static Object valueObjectFromByteArray(ObjectType type, DataInputStream dis) throws IOException {
        Object result = null;
        switch (type) {
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BOOLEAN:
                result = ValueBytesConverter.valueObjectFromByteArray(ModuleUtils.convertTo(type), dis, -1);
                break;
            case BIG_INTEGER:
            case BIG_DECIMAL:
            case STRING:
            case BYTES:
                result = ValueBytesConverter.valueObjectFromByteArray(ModuleUtils.convertTo(type), dis, dis.readInt());
                break;
            case OBJECT_ELEMENT:
                result = bytesToObjectElement(dis);
                break;
            case OBJECT_ARRAY:
                result = objectArrayFromByteArray(dis);
                break;
            default:
                throw new RuntimeException("wrong type " + type);
        }
        return result;
    }

    public static ObjectArray objectArrayFromByteArray(DataInputStream dis) throws IOException {
        ObjectArray result = null;

        if (dis.available() == 0)
            return result;

        ObjectType objectType = ObjectType.values()[dis.readInt()];
        int count = dis.readInt();
        result = new ObjectArray(count, objectType);

        for (int i = 0; i < count; i++) {
            ObjectType type = objectType;
            if (type == ObjectType.VALUE_ANY)
                type = ObjectType.values()[dis.readInt()];
            Object o = valueObjectFromByteArray(type, dis);
            switch (objectType) {
                case OBJECT_ARRAY:
                    result.add((ObjectArray) o);
                    break;
                case OBJECT_ELEMENT:
                    result.add((ObjectElement) o);
                    break;
                case STRING:
                    result.add((String) o);
                    break;
                case BYTE:
                    result.add((Byte) o);
                    break;
                case SHORT:
                    result.add((Short) o);
                    break;
                case INTEGER:
                    result.add((Integer) o);
                    break;
                case LONG:
                    result.add((Long) o);
                    break;
                case FLOAT:
                    result.add((Float) o);
                    break;
                case DOUBLE:
                    result.add((Double) o);
                    break;
                case BIG_INTEGER:
                    result.add((BigInteger) o);
                    break;
                case BIG_DECIMAL:
                    result.add((BigDecimal) o);
                    break;
                case BYTES:
                    result.add((byte[]) o);
                    break;
                case BOOLEAN:
                    result.add((Boolean) o);
                    break;
            }
        }
        return result;
    }

    public static ObjectElement objectElementFromByteArray(byte[] value) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(value); DataInputStream dis = new DataInputStream(bais)) {
            return bytesToObjectElement(dis);
        } catch (Exception ignore) {
        }
        return new ObjectElement();
    }

    private static ObjectElement bytesToObjectElement(DataInputStream dis) throws IOException {
        ObjectElement result = null;

        if (dis.available() == 0)
            return result;

        int count = dis.readInt();
        result = new ObjectElement();

        for (int i = 0; i < count; i++) {
            ObjectField objectField = new ObjectField(new String(dis.readNBytes(dis.readInt())));
            int typeInt = dis.readInt();
            // try {
            if (typeInt >= 1000) {
                ObjectType objectType = ObjectType.values()[typeInt - 1000];
                objectField.setValue(objectType, null);
            } else {
                ObjectType objectType = ObjectType.values()[typeInt];
                objectField.setValue(objectType, valueObjectFromByteArray(objectType, dis));
            }
            /*
            } catch (Exception e) {
                // logger.warn("error field={}, type={}", objectField.getName(), objectField.getType(), e);
                throw e;
            }
            */
            result.getFields().add(objectField);
        }

        return result;
    }

}