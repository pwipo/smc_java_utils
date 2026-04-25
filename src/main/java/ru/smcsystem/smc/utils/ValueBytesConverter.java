package ru.smcsystem.smc.utils;

import ru.smcsystem.api.dto.ObjectArray;
import ru.smcsystem.api.enumeration.ValueType;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Objects;

public class ValueBytesConverter {
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    public static byte[] toByteArray(ValueType valueType, Object value) {
        Objects.requireNonNull(valueType, "valueType not set");
        Objects.requireNonNull(value, "value not set");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); DataOutputStream dos = new DataOutputStream(baos)) {
            toByteArray(valueType, value, dos);
            dos.flush();
            return baos.toByteArray();
        } catch (Exception ignore) {
        }
        return null;
    }

    public static void toByteArray(ValueType valueType, Object value, DataOutputStream dos) throws IOException {
        Objects.requireNonNull(valueType, "valueType not set");
        Objects.requireNonNull(value, "value not set");

        switch (valueType) {
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case BIG_INTEGER:
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
                toByteArray(valueType, (Number) value, dos);
                break;
            case STRING:
                dos.write(((String) value).getBytes());
                break;
            case BYTES:
                dos.write((byte[]) value);
                break;
            case OBJECT_ARRAY:
                ObjectArrayBytesConverter.toByteArray((ObjectArray) value, dos);
                break;
            case BOOLEAN:
                dos.writeBoolean((Boolean) value);
                break;
            default:
                throw new RuntimeException("wrong type " + valueType);
        }
    }

    public static void toByteArray(ValueType valueType, Number value, DataOutputStream dos) throws IOException {
        Objects.requireNonNull(valueType, "valueType not set");
        Objects.requireNonNull(value, "value not set");
        Objects.requireNonNull(dos, "dos not set");

        switch (valueType) {
            case BYTE:
                dos.writeByte((Byte) value);
                break;
            case SHORT:
                dos.writeShort((Short) value);
                break;
            case INTEGER:
                dos.writeInt((Integer) value);
                break;
            case LONG:
                dos.writeLong((Long) value);
                break;
            case BIG_INTEGER:
                dos.write(((BigInteger) value).toByteArray());
                break;
            case FLOAT:
                dos.writeFloat((Float) value);
                break;
            case DOUBLE:
                dos.writeDouble((Double) value);
                break;
            case BIG_DECIMAL:
                dos.write(bigDecimalToByte((BigDecimal) value));
                break;
            default:
                throw new RuntimeException("wrong type " + valueType);
        }
    }

    public static byte[] bigDecimalToByte(BigDecimal num) {
        BigInteger sig = new BigInteger(num.unscaledValue().toString());
        int scale = num.scale();
        byte[] bscale = new byte[]{
                (byte) (scale >>> 24),
                (byte) (scale >>> 16),
                (byte) (scale >>> 8),
                (byte) (scale)
        };
        return (byte[]) addAll(bscale, sig.toByteArray());
    }

    public static Object valueObjectFromByteArray(ValueType type, byte[] valueArray) {
        Object result = null;
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(valueArray))) {
            result = valueObjectFromByteArray(type, dis, valueArray.length);
        } catch (Exception ignore) {
        }
        return result;
    }

    public static Object valueObjectFromByteArray(ValueType type, DataInputStream dis, int size) {
        Object result = null;
        try {
            switch (type) {
                case STRING:
                    result = dis.available() >= size ? new String(dis.readNBytes(size)) : "";
                    break;
                case BYTE:
                case SHORT:
                case INTEGER:
                case LONG:
                case BIG_INTEGER:
                case FLOAT:
                case DOUBLE:
                case BIG_DECIMAL:
                    result = bytesToValueNumber2(type, dis, size);
                    break;
                case BYTES:
                    result = dis.available() >= size ? dis.readNBytes(size) : null;
                    break;
                case OBJECT_ARRAY:
                    result = ObjectArrayBytesConverter.objectArrayFromByteArray(dis);
                    break;
                case BOOLEAN:
                    result = bytesToValueBoolean(dis.readNBytes(1));
                    break;
            }
        } catch (Exception ignore) {
        }
        return result;
    }

    public static Number bytesToValueNumber2(ValueType valueType, DataInputStream dis, int size) throws IOException {
        Number result = null;
        switch (valueType) {
            case BYTE:
                result = dis.readByte();
                break;
            case SHORT:
                result = dis.readShort();
                break;
            case INTEGER:
                result = dis.readInt();
                break;
            case LONG:
                result = dis.readLong();
                break;
            case BIG_INTEGER:
                result = dis.available() >= size ? new BigInteger(dis.readNBytes(size)) : BigInteger.ZERO;
                break;
            case FLOAT:
                result = dis.readFloat();
                break;
            case DOUBLE:
                result = dis.readDouble();
                break;
            case BIG_DECIMAL:
                result = dis.available() >= size ? byteToBigDecimal(dis.readNBytes(size)) : BigDecimal.ZERO;
                break;
            default:
                throw new RuntimeException("wrong type " + valueType);
        }
        return result;
    }

    public static Boolean bytesToValueBoolean(byte[] bytes) {
        return bytes != null && bytes.length > 0 && bytes[0] > 0;
    }

    public static BigDecimal byteToBigDecimal(byte[] raw) {
        int scale = (raw[0] & 0xFF) << 24 |
                (raw[1] & 0xFF) << 16 |
                (raw[2] & 0xFF) << 8 |
                (raw[3] & 0xFF);
        BigInteger sig = new BigInteger(subarray(raw, 4, raw.length));
        return new BigDecimal(sig, scale);
    }

    // from apache commons ArrayUtils
    public static byte[] addAll(final byte[] array1, final byte... array2) {
        if (array1 == null) {
            return clone(array2);
        } else if (array2 == null) {
            return clone(array1);
        }
        final byte[] joinedArray = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, joinedArray, 0, array1.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }

    // from apache commons ArrayUtils
    public static byte[] clone(final byte[] array) {
        if (array == null)
            return null;
        return array.clone();
    }

    // from apache commons ArrayUtils
    public static byte[] subarray(byte[] array, int startIndexInclusive, int endIndexExclusive) {
        if (array == null)
            return null;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive > array.length)
            endIndexExclusive = array.length;
        int newSize = endIndexExclusive - startIndexInclusive;
        if (newSize <= 0)
            return EMPTY_BYTE_ARRAY;

        byte[] subarray = new byte[newSize];
        System.arraycopy(array, startIndexInclusive, subarray, 0, newSize);
        return subarray;
    }

}
