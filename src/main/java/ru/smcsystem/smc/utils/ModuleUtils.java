package ru.smcsystem.smc.utils;

import ru.smcsystem.api.dto.*;
import ru.smcsystem.api.enumeration.*;
import ru.smcsystem.api.tools.ConfigurationTool;
import ru.smcsystem.api.tools.execution.ExecutionContextTool;
import ru.smcsystem.smc.utils.converter.SmcConverter;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModuleUtils {
    public static Number getNumber(IMessage m) {
        return getNumber((IValue) m);
    }

    public static String getString(IMessage m) {
        return getString((IValue) m);
    }

    public static byte[] getBytes(IMessage m) {
        return getBytes((IValue) m);
    }

    public static Boolean getBoolean(IMessage m) {
        return getBoolean((IValue) m);
    }

    public static String toString(IMessage m) {
        return toString((IValue) m);
    }

    public static Boolean toBoolean(IMessage m) {
        return toBoolean((IValue) m);
    }

    public static Number toNumber(IMessage m) {
        return toNumber((IValue) m);
    }

    public static ObjectArray getObjectArray(IMessage m) {
        return getObjectArray((IValue) m);
    }

    public static Number getNumber(IValue m) {
        return isNumber(m) ? (Number) m.getValue() : null;
    }

    public static String getString(IValue m) {
        return isString(m) ? (String) m.getValue() : null;
    }

    public static byte[] getBytes(IValue m) {
        return isBytes(m) ? (byte[]) m.getValue() : null;
    }

    public static Boolean getBoolean(IValue m) {
        return isBoolean(m) ? (Boolean) m.getValue() : null;
    }

    public static String toString(IValue m) {
        if (m == null)
            return "";
        String result;
        switch (m.getType()) {
            case STRING:
                result = (String) m.getValue();
                break;
            case BYTES:
                result = Base64.getEncoder().encodeToString((byte[]) m.getValue());
                break;
            default:
                result = m.getValue().toString();
                break;
        }
        return result;
    }

    public static Boolean toBoolean(IValue m) {
        if (m == null)
            return false;
        Boolean result;
        switch (m.getType()) {
            case STRING:
                result = Boolean.parseBoolean((String) m.getValue());
                break;
            case BOOLEAN:
                result = (Boolean) m.getValue();
                break;
            case BYTES:
            case OBJECT_ARRAY:
                result = true;
                break;
            default:
                result = ((Number) m.getValue()).intValue() > 0;
                break;
        }
        return result;
    }

    public static Number toNumber(IValue m) {
        if (m == null)
            return 0;
        Number result;
        switch (m.getType()) {
            case STRING: {
                String value = (String) m.getValue();
                if (!value.isBlank()) {
                    try {
                        if (value.contains(".")) {
                            result = Double.parseDouble(value);
                        } else {
                            result = Long.parseLong(value);
                        }
                    } catch (Exception e) {
                        result = 0;
                    }
                } else {
                    result = 0;
                }
                break;
            }
            case BOOLEAN:
                result = (Boolean) m.getValue() ? 1 : 0;
                break;
            case BYTES:
                result = ((byte[]) m.getValue()).length;
                break;
            case OBJECT_ARRAY:
                result = ((ObjectArray) m.getValue()).size();
                break;
            default:
                result = (Number) m.getValue();
                break;
        }
        return result;
    }

    public static ObjectArray getObjectArray(IValue m) {
        return isObjectArray(m) ? (ObjectArray) m.getValue() : null;
    }

    public static ObjectArray toObjectArray(IValue m) {
        if (m == null)
            return new ObjectArray();
        ObjectArray objectArray = null;
        switch (m.getType()) {
            case STRING:
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case BIG_INTEGER:
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
            case BYTES:
            case BOOLEAN:
                objectArray = new ObjectArray(List.of(m.getValue()), convertTo(m.getType()));
                break;
            case OBJECT_ARRAY:
                objectArray = (ObjectArray) m.getValue();
                break;
        }
        return objectArray != null ? objectArray : new ObjectArray();
    }

    public static ObjectElement toObjectElement(IValue m) {
        if (m == null)
            return new ObjectElement();
        ObjectElement objectElement = null;
        switch (m.getType()) {
            case STRING:
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case BIG_INTEGER:
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
            case BYTES:
            case BOOLEAN: {
                ObjectField objectField = new ObjectField("0");
                objectField.setValue(convertTo(m.getType()), m.getValue());
                objectElement = new ObjectElement(List.of(objectField));
                break;
            }
            case OBJECT_ARRAY: {
                ObjectArray objectArray = (ObjectArray) m.getValue();
                if (isArrayContainObjectElements(objectArray)) {
                    objectElement = (ObjectElement) objectArray.get(0);
                } else if (objectArray.isSimple() && objectArray.size() > 0) {
                    objectElement = new ObjectElement();
                    for (int i = 0; i < objectArray.size(); i++)
                        objectElement.getFields().add(new ObjectField(String.valueOf(i), objectArray.getType(), objectArray.get(i)));
                }
                break;
            }
        }
        return objectElement != null ? objectElement : new ObjectElement();
    }

    /**
     * deserialize messages to object array
     * if first message type ObjectArray, when return it
     * use object serialization format
     * @formatter:off
     * object serialization format:
     *      number - type of elements, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[], 14-OBJECT_ELEMENT_OPTIMIZED, 15-OBJECT_ELEMENT_SIMPLE_OPTIMIZED, 16-Boolean
     *      number - number of item in array
     *      items. depend of type. format:
     *          if item type is 0 (ObjectArray): then list of arrays. each has format described above, recursion.
     *          if item type is 1 (ObjectElement): then list of objects:
     *              if all elements hase same fields and size>1 then:
     *                  number - number of fields in object.
     *                  list of fields. format:
     *                      string - field name.
     *                      number - field type, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[], 16-Boolean
     *                  list of values for each element. format:
     *                      field value. depend of type. format:
     *                          if item type is 0-ObjectArray: then list of arrays. each has format described above, recursion.
     *                          if item type is 1-ObjectElement: then list of objects. each has format described above, recursion.
     *                          if item type is 2-ObjectElementSimple: then list of simple objects. each has format described above, recursion.
     *                          else: any type - simple value.
     *              else:
     *                  number - number of fields in object.
     *                  for each element:
     *                      for each field. format:
     *                          string - field name.
     *                          number - field type, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[], 16-Boolean
     *                          field value. depend of type. format:
     *                              if item type is 0-ObjectArray: then list of arrays. each has format described above, recursion.
     *                              if item type is 1-ObjectElement: then list of objects. each has format described above, recursion.
     *                              if item type is 2-ObjectElementSimple: then list of simple objects. each has format described above, recursion.
     *                              else: any type - simple value.
     *          if item type is 2 (ObjectElementSimple): then list of simple objects has format:
     *              if all elements hase same fields and size>1 then:
     *                  number - number of fields in object.
     *                  list of fields. format:
     *                      string - field name.
     *                  list of values for each element. format:
     *                      any type - simple value.
     *              else:
     *                  number - number of fields in each object (one for all).
     *                  list of each object fields. format:
     *                      string - field name.
     *                      any type - simple value.
     *          else: list of simple values, format:
     *              any type - simple value.
     * @formatter:on
     *
     * @param messages - list of messages for deserialization
     * @return ObjectArray
     */
    public static ObjectArray deserializeToObject(LinkedList<IMessage> messages) {
        IMessage m = messages.peek();
        if (isObjectArray(m))
            return getObjectArray(messages.poll());
        ObjectArray objectArray = new ObjectArray();
        if (messages.size() < 2)
            return objectArray;
        Number typeId = getNumber(m);
        if (typeId == null)
            return objectArray;
        int typeIdI = typeId.intValue();
        if (typeIdI < 0 || ObjectTypePrivate.values().length <= typeIdI)
            return objectArray;
        ObjectTypePrivate typePrivate = ObjectTypePrivate.values()[typeIdI];
        messages.poll();

        Number size = getNumber(messages.peek());
        if (size == null) {
            messages.addFirst(m);
            return objectArray;
        }
        messages.poll();
        int count = size.intValue();

        objectArray = new ObjectArray(count, typePrivate.convert());
        try {
            switch (typePrivate) {
                case OBJECT_ARRAY:
                    for (int i = 0; i < count; i++)
                        objectArray.add(deserializeToObject(messages));
                    break;
                case OBJECT_ELEMENT:
                    for (int i = 0; i < count; i++)
                        objectArray.add(deserializeToObjectElement(messages, -1, null));
                    break;
                case OBJECT_ELEMENT_SIMPLE: {
                    Number countFields = getNumber(messages.peek());
                    if (countFields != null) {
                        messages.poll();
                        for (int i = 0; i < count; i++)
                            objectArray.add(deserializeToObjectElement(messages, countFields.intValue(), null));
                    }
                    break;
                }
                case VALUE_ANY:
                case STRING:
                case BYTE:
                case SHORT:
                case INTEGER:
                case LONG:
                case FLOAT:
                case DOUBLE:
                case BIG_INTEGER:
                case BIG_DECIMAL:
                case BYTES:
                case BOOLEAN:
                    for (int i = 0; i < count; i++)
                        objectArray.add(messages.poll());
                    break;
                case OBJECT_ELEMENT_OPTIMIZED: {
                    Number countFields = getNumber(messages.peek());
                    if (countFields == null)
                        break;
                    int countFieldsI = countFields.intValue();
                    if (messages.size() <= countFieldsI * 2 + 1)
                        break;
                    messages.poll();
                    List<Map.Entry<String, ObjectTypePrivate>> definedFields = new ArrayList<>(countFieldsI + 1);
                    boolean hasErrors = false;
                    for (int i = 0; i < countFieldsI; i++) {
                        String fieldName = toString(messages.poll());
                        Number fieldType = getNumber(messages.peek());
                        if (fieldType == null) {
                            hasErrors = true;
                            break;
                        }
                        int fieldTypeI = fieldType.intValue();
                        if (fieldTypeI < 0 || ObjectTypePrivate.values().length <= fieldTypeI) {
                            hasErrors = true;
                            break;
                        }
                        messages.poll();
                        definedFields.add(Map.entry(fieldName, ObjectTypePrivate.values()[fieldTypeI]));
                    }
                    if (hasErrors)
                        break;
                    for (int i = 0; i < count; i++)
                        objectArray.add(deserializeToObjectElement(messages, -1, definedFields));
                    break;
                }
                case OBJECT_ELEMENT_SIMPLE_OPTIMIZED: {
                    Number countFields = getNumber(messages.peek());
                    if (countFields == null)
                        break;
                    int countFieldsI = countFields.intValue();
                    if (messages.size() <= countFieldsI + 1)
                        break;
                    messages.poll();
                    List<Map.Entry<String, ObjectTypePrivate>> definedFields = new ArrayList<>(countFieldsI + 1);
                    for (int i = 0; i < countFieldsI; i++)
                        definedFields.add(Map.entry(toString(messages.poll()), ObjectTypePrivate.VALUE_ANY));
                    for (int i = 0; i < count; i++)
                        objectArray.add(deserializeToObjectElement(messages, -1, definedFields));
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return objectArray;
    }

    private static ObjectElement deserializeToObjectElement(LinkedList<IMessage> messages, int countFields, List<Map.Entry<String, ObjectTypePrivate>> definedFields) {
        IMessage m = messages.peek();
        if (isObjectArray(m)) {
            ObjectArray objectArray = getObjectArray(messages.poll());
            if (isArrayContainObjectElements(objectArray))
                return (ObjectElement) objectArray.get(0);
            return new ObjectElement();
        }
        ObjectElement objectElement = new ObjectElement();

        int count = -1;
        if (countFields < 0) {
            if (definedFields == null) {
                Number size = getNumber(messages.peek());
                if (size == null)
                    return objectElement;
                messages.poll();
                count = size.intValue();
            } else {
                count = definedFields.size();
            }
        } else {
            count = countFields;
        }

        try {
            if (definedFields != null) {
                definedFields.forEach(entry -> deserializeToObjectElementValue(messages, objectElement, entry.getKey(), entry.getValue()));
            } else {
                for (int i = 0; i < count; i++) {
                    if ((countFields > -1 && messages.size() < 2) || (countFields < 0 && messages.size() < 3))
                        break;
                    String fieldName = toString(messages.poll());
                    ObjectTypePrivate type = ObjectTypePrivate.VALUE_ANY;
                    if (countFields < 0) {
                        Number typeId = getNumber(messages.peek());
                        if (typeId == null)
                            break;
                        int typeIdI = typeId.intValue();
                        if (typeIdI < 0 || ObjectTypePrivate.values().length <= typeIdI)
                            break;
                        messages.poll();
                        type = ObjectTypePrivate.values()[typeId.intValue()];
                    }
                    deserializeToObjectElementValue(messages, objectElement, fieldName, type);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return objectElement;
    }

    private static void deserializeToObjectElementValue(LinkedList<IMessage> messages, ObjectElement objectElement, String fieldName, ObjectTypePrivate type) {
        switch (type) {
            case OBJECT_ARRAY:
                objectElement.getFields().add(new ObjectField(fieldName, deserializeToObject(messages)));
                break;
            case OBJECT_ELEMENT:
                objectElement.getFields().add(new ObjectField(fieldName, deserializeToObjectElement(messages, -1, null)));
                break;
            case OBJECT_ELEMENT_SIMPLE: {
                Number countFields2 = getNumber(messages.peek());
                if (countFields2 != null) {
                    messages.poll();
                    objectElement.getFields().add(new ObjectField(fieldName, deserializeToObjectElement(messages, countFields2.intValue(), null)));
                }
                break;
            }
            case OBJECT_ELEMENT_OPTIMIZED:
                break;
            case OBJECT_ELEMENT_SIMPLE_OPTIMIZED:
                break;
            case VALUE_ANY:
            case STRING:
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BIG_INTEGER:
            case BIG_DECIMAL:
            case BYTES:
            case BOOLEAN:
                objectElement.getFields().add(new ObjectField(fieldName, messages.poll()));
                break;
            case STRING_NULL:
                objectElement.getFields().add(new ObjectField(fieldName, ObjectType.STRING, null));
                break;
            case BYTE_NULL:
                objectElement.getFields().add(new ObjectField(fieldName, ObjectType.BYTE, null));
                break;
            case SHORT_NULL:
                objectElement.getFields().add(new ObjectField(fieldName, ObjectType.SHORT, null));
                break;
            case INTEGER_NULL:
                objectElement.getFields().add(new ObjectField(fieldName, ObjectType.INTEGER, null));
                break;
            case LONG_NULL:
                objectElement.getFields().add(new ObjectField(fieldName, ObjectType.LONG, null));
                break;
            case FLOAT_NULL:
                objectElement.getFields().add(new ObjectField(fieldName, ObjectType.FLOAT, null));
                break;
            case DOUBLE_NULL:
                objectElement.getFields().add(new ObjectField(fieldName, ObjectType.DOUBLE, null));
                break;
            case BIG_INTEGER_NULL:
                objectElement.getFields().add(new ObjectField(fieldName, ObjectType.BIG_INTEGER, null));
                break;
            case BIG_DECIMAL_NULL:
                objectElement.getFields().add(new ObjectField(fieldName, ObjectType.BIG_DECIMAL, null));
                break;
            case BYTES_NULL:
                objectElement.getFields().add(new ObjectField(fieldName, ObjectType.BYTES, null));
                break;
            case BOOLEAN_NULL:
                objectElement.getFields().add(new ObjectField(fieldName, ObjectType.BOOLEAN, null));
                break;
        }
    }

    /**
     * serialize objects to messages
     * use object serialization format
     * @formatter:off
     * object serialization format:
     *      number - type of elements, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[], 14-OBJECT_ELEMENT_OPTIMIZED, 15-OBJECT_ELEMENT_SIMPLE_OPTIMIZED, 16-Boolean
     *      number - number of item in array
     *      items. depend of type. format:
     *          if item type is 0 (ObjectArray): then list of arrays. each has format described above, recursion.
     *          if item type is 1 (ObjectElement): then list of objects:
     *              if all elements hase same fields and size>1 then:
     *                  number - number of fields in object.
     *                  list of fields. format:
     *                      string - field name.
     *                      number - field type, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[], 16-Boolean
     *                  list of values for each element. format:
     *                      field value. depend of type. format:
     *                          if item type is 0-ObjectArray: then list of arrays. each has format described above, recursion.
     *                          if item type is 1-ObjectElement: then list of objects. each has format described above, recursion.
     *                          if item type is 2-ObjectElementSimple: then list of simple objects. each has format described above, recursion.
     *                          else: any type - simple value.
     *              else:
     *                  number - number of fields in object.
     *                  for each element:
     *                      for each field. format:
     *                          string - field name.
     *                          number - field type, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[], 16-Boolean
     *                          field value. depend of type. format:
     *                              if item type is 0-ObjectArray: then list of arrays. each has format described above, recursion.
     *                              if item type is 1-ObjectElement: then list of objects. each has format described above, recursion.
     *                              if item type is 2-ObjectElementSimple: then list of simple objects. each has format described above, recursion.
     *                              else: any type - simple value.
     *          if item type is 2 (ObjectElementSimple): then list of simple objects has format:
     *              if all elements hase same fields and size>1 then:
     *                  number - number of fields in object.
     *                  list of fields. format:
     *                      string - field name.
     *                  list of values for each element. format:
     *                      any type - simple value.
     *              else:
     *                  number - number of fields in each object (one for all).
     *                  list of each object fields. format:
     *                      string - field name.
     *                      any type - simple value.
     *          else: list of simple values, format:
     *              any type - simple value.
     * @formatter:on
     *
     * @param mainList - list of objects for serialization
     * @return List of values, ready for send as messages
     */
    public static List<Object> serializeFromObject(ObjectArray mainList) {
        List<Object> result = new LinkedList<>();
        if (mainList == null)
            return result;
        ObjectTypePrivate typePrivate = toObjectTypePrivate(mainList.getType(), false);
        result.add(typePrivate.ordinal());
        result.add(mainList.size());
        if (mainList.size() == 0)
            return result;
        boolean hasFieldWithNull = false;
        if (typePrivate == ObjectTypePrivate.OBJECT_ELEMENT && mainList.size() > 1) {
            boolean isSimple = true;
            List<String> fieldNames = null;
            for (int i = 0; i < mainList.size(); i++) {
                ObjectElement objectElement = (ObjectElement) mainList.get(i);
                if (i == 0) {
                    fieldNames = objectElement.getFields().stream().map(ObjectField::getName).collect(Collectors.toList());
                    if (!objectElement.isSimple()) {
                        isSimple = false;
                        break;
                    }
                    if (objectElement.getFields().stream().anyMatch(f -> f.getValue() == null)) {
                        isSimple = false;
                        hasFieldWithNull = true;
                        break;
                    }
                } else {
                    if (!objectElement.isSimple() || !objectElement.getFields().stream().map(ObjectField::getName).collect(Collectors.toList()).equals(fieldNames)) {
                        isSimple = false;
                        break;
                    }
                    if (objectElement.getFields().stream().anyMatch(f -> f.getValue() == null)) {
                        isSimple = false;
                        hasFieldWithNull = true;
                        break;
                    }
                }
            }
            if (isSimple)
                typePrivate = ObjectTypePrivate.OBJECT_ELEMENT_SIMPLE;
        }
        switch (typePrivate) {
            case OBJECT_ARRAY:
                for (int i = 0; i < mainList.size(); i++)
                    result.addAll(serializeFromObject((ObjectArray) mainList.get(i)));
                break;
            case OBJECT_ELEMENT: {
                List<String> definedFields = null;
                if (!hasFieldWithNull && mainList.size() > 1 && isSameFields(mainList)) {
                    List<Map.Entry<String, ObjectType>> definedFieldsTmp = new ArrayList<>(mainList.size() + 1);
                    ObjectElement objectElement = (ObjectElement) mainList.get(0);
                    objectElement.getFields().forEach(f -> definedFieldsTmp.add(Map.entry(f.getName(), f.getType())));
                    result.clear();
                    result.add(ObjectTypePrivate.OBJECT_ELEMENT_OPTIMIZED.ordinal());
                    result.add(mainList.size());
                    result.add(definedFieldsTmp.size());
                    definedFieldsTmp.forEach(f -> {
                        result.add(f.getKey());
                        result.add(toObjectTypePrivate(f.getValue(), false).ordinal());
                    });
                    definedFields = definedFieldsTmp.stream().map(Map.Entry::getKey).collect(Collectors.toList());
                }
                for (int i = 0; i < mainList.size(); i++)
                    result.addAll(serializeFromObject((ObjectElement) mainList.get(i), false, definedFields));
                break;
            }
            case OBJECT_ELEMENT_SIMPLE:
                List<String> definedFields = null;
                if (mainList.size() > 1 && isSameFields(mainList)) {
                    List<String> definedFieldsTmp = new ArrayList<>(mainList.size() + 1);
                    ObjectElement objectElement = (ObjectElement) mainList.get(0);
                    objectElement.getFields().forEach(f -> definedFieldsTmp.add(f.getName()));
                    result.clear();
                    result.add(ObjectTypePrivate.OBJECT_ELEMENT_SIMPLE_OPTIMIZED.ordinal());
                    result.add(mainList.size());
                    result.add(definedFieldsTmp.size());
                    result.addAll(definedFieldsTmp);
                    definedFields = definedFieldsTmp;
                } else {
                    result.add(mainList.size() > 0 ? ((ObjectElement) mainList.get(0)).getFields().size() : 0);
                }
                for (int i = 0; i < mainList.size(); i++)
                    result.addAll(serializeFromObject((ObjectElement) mainList.get(i), true, definedFields));
                break;
            case VALUE_ANY:
            case STRING:
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case BIG_INTEGER:
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
            case BYTES:
            case BOOLEAN: {
                for (int i = 0; i < mainList.size(); i++)
                    result.add(mainList.get(i));
                break;
            }
        }
        return result;
    }

    private static List<Object> serializeFromObject(ObjectElement objectElement, boolean isSimple, List<String> definedFields) {
        List<Object> result = new LinkedList<>();
        if (objectElement == null)
            return result;
        if (definedFields != null) {
            // because its check
            // definedFields.stream()
            //         .map(objectElement::findField)
            //         .filter(Optional::isPresent)
            //         .map(Optional::get)
            objectElement.getFields().forEach(f -> result.addAll(serializeFromObjectFieldValue(f.getType(), f.getValue())));
        } else {
            if (!isSimple)
                result.add(objectElement.getFields().size());
            objectElement.getFields().forEach(objField -> {
                result.add(objField.getName());
                if (!isSimple)
                    result.add(toObjectTypePrivate(objField.getType(), objField.getValue() == null).ordinal());
                if (objField.getValue() != null)
                    result.addAll(serializeFromObjectFieldValue(objField.getType(), objField.getValue()));
            });
        }
        return result;
    }

    private static List<Object> serializeFromObjectFieldValue(ObjectType type, Object value) {
        switch (type) {
            case OBJECT_ARRAY:
                return serializeFromObject((ObjectArray) value);
            case OBJECT_ELEMENT:
                if (((ObjectElement) value).isSimple()) {
                    List<Object> result = new LinkedList<>();
                    result.add(((ObjectElement) value).getFields().size());
                    result.addAll(serializeFromObject((ObjectElement) value, true, null));
                    return result;

                } else {
                    return serializeFromObject((ObjectElement) value, false, null);
                }
            case VALUE_ANY:
            case STRING:
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case BIG_INTEGER:
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
            case BYTES:
            case BOOLEAN:
                return List.of(value);
        }
        throw new IllegalArgumentException(type.name());
    }

    private static ObjectTypePrivate toObjectTypePrivate(ObjectType type, boolean isNull) {
        switch (type) {
            case OBJECT_ARRAY:
                return ObjectTypePrivate.OBJECT_ARRAY;
            case OBJECT_ELEMENT:
                return ObjectTypePrivate.OBJECT_ELEMENT;
            case VALUE_ANY:
                return ObjectTypePrivate.VALUE_ANY;
            case STRING:
                return isNull ? ObjectTypePrivate.STRING_NULL : ObjectTypePrivate.STRING;
            case BYTE:
                return isNull ? ObjectTypePrivate.BYTE_NULL : ObjectTypePrivate.BYTE;
            case SHORT:
                return isNull ? ObjectTypePrivate.SHORT_NULL : ObjectTypePrivate.SHORT;
            case INTEGER:
                return isNull ? ObjectTypePrivate.INTEGER_NULL : ObjectTypePrivate.INTEGER;
            case LONG:
                return isNull ? ObjectTypePrivate.LONG_NULL : ObjectTypePrivate.LONG;
            case FLOAT:
                return isNull ? ObjectTypePrivate.FLOAT_NULL : ObjectTypePrivate.FLOAT;
            case DOUBLE:
                return isNull ? ObjectTypePrivate.DOUBLE_NULL : ObjectTypePrivate.DOUBLE;
            case BIG_INTEGER:
                return isNull ? ObjectTypePrivate.BIG_INTEGER_NULL : ObjectTypePrivate.BIG_INTEGER;
            case BIG_DECIMAL:
                return isNull ? ObjectTypePrivate.BIG_DECIMAL_NULL : ObjectTypePrivate.BIG_DECIMAL;
            case BYTES:
                return isNull ? ObjectTypePrivate.BYTES_NULL : ObjectTypePrivate.BYTES;
            case BOOLEAN:
                return isNull ? ObjectTypePrivate.BOOLEAN_NULL : ObjectTypePrivate.BOOLEAN;
        }
        return ObjectTypePrivate.valueOf(type.name());
    }

    public static boolean isNumber(IMessage m) {
        return isNumber((IValue) m);
    }

    public static boolean isString(IMessage m) {
        return isString((IValue) m);
    }

    public static boolean isBytes(IMessage m) {
        return isBytes((IValue) m);
    }

    public static boolean isBoolean(IMessage m) {
        return isBoolean((IValue) m);
    }

    public static boolean isObjectArray(IMessage m) {
        return isObjectArray((IValue) m);
    }

    public static boolean isNumber(IValue m) {
        return m != null && ((ValueType.BYTE.equals(m.getType()) || ValueType.SHORT.equals(m.getType()) || ValueType.INTEGER.equals(m.getType()) || ValueType.LONG.equals(m.getType()) || ValueType.FLOAT.equals(m.getType()) || ValueType.DOUBLE.equals(m.getType()) || ValueType.BIG_INTEGER.equals(m.getType()) || ValueType.BIG_DECIMAL.equals(m.getType())));
    }

    public static boolean isString(IValue m) {
        return m != null && ValueType.STRING.equals(m.getType());
    }

    public static boolean isBytes(IValue m) {
        return m != null && ValueType.BYTES.equals(m.getType());
    }

    public static boolean isBoolean(IValue m) {
        return m != null && ValueType.BOOLEAN.equals(m.getType());
    }

    public static boolean isObjectArray(IValue m) {
        return m != null && ValueType.OBJECT_ARRAY.equals(m.getType());
    }

    public static boolean hasErrors(ICommand c) {
        if (c == null)
            return false;
        return !c.getActions().isEmpty() && c.getActions().stream().anyMatch(ModuleUtils::hasErrors);
    }

    public static boolean hasData(ICommand c) {
        if (c == null)
            return false;
        return !c.getActions().isEmpty() && c.getActions().stream().anyMatch(ModuleUtils::hasData);
    }

    public static boolean hasErrors(IAction a) {
        if (a == null)
            return false;
        return !a.getMessages().isEmpty()
                // && ActionType.EXECUTE.equals(a.getType())
                // && a.getMessages().stream().anyMatch(m -> MessageType.DATA.equals(m.getMessageType()))
                && a.getMessages().stream().anyMatch(m -> MessageType.ERROR.equals(m.getMessageType()) || MessageType.ACTION_ERROR.equals(m.getMessageType()));
    }

    public static boolean hasData(IAction a) {
        if (a == null)
            return false;
        return !a.getMessages().isEmpty()
                && ActionType.EXECUTE.equals(a.getType())
                && a.getMessages().stream().anyMatch(m -> MessageType.DATA.equals(m.getMessageType()));
    }

    public static List<IMessage> getErrors(IAction a) {
        if (a == null)
            return List.of();
        return a.getMessages().stream()
                .filter(m -> MessageType.ERROR.equals(m.getMessageType()) || MessageType.ACTION_ERROR.equals(m.getMessageType()))
                .collect(Collectors.toList());
    }

    public static List<List<IMessage>> getErrors(ICommand c) {
        if (c == null)
            return List.of();
        return c.getActions().stream()
                .map(ModuleUtils::getErrors)
                .filter(l -> !l.isEmpty())
                .collect(Collectors.toList());
    }

    public static List<IMessage> getData(IAction a) {
        if (a == null)
            return List.of();
        return a.getMessages().stream()
                .filter(m -> MessageType.DATA.equals(m.getMessageType()))
                .collect(Collectors.toList());
    }

    public static List<List<IMessage>> getData(ICommand c) {
        if (c == null)
            return List.of();
        return c.getActions().stream()
                .map(ModuleUtils::getData)
                .filter(l -> !l.isEmpty())
                .collect(Collectors.toList());
    }

    public static boolean isArrayContainObjectElements(ObjectArray objectArray) {
        return objectArray != null && objectArray.size() > 0 && ObjectType.OBJECT_ELEMENT == objectArray.getType();
    }

    public static boolean isArrayContainArrays(ObjectArray objectArray) {
        return objectArray != null && objectArray.size() > 0 && ObjectType.OBJECT_ARRAY == objectArray.getType();
    }

    public static boolean isNumber(ObjectField m) {
        return m != null && ((ObjectType.BYTE.equals(m.getType()) || ObjectType.SHORT.equals(m.getType()) || ObjectType.INTEGER.equals(m.getType()) || ObjectType.LONG.equals(m.getType()) || ObjectType.FLOAT.equals(m.getType()) || ObjectType.DOUBLE.equals(m.getType()) || ObjectType.BIG_INTEGER.equals(m.getType()) || ObjectType.BIG_DECIMAL.equals(m.getType())));
    }

    public static boolean isString(ObjectField m) {
        return m != null && ObjectType.STRING.equals(m.getType());
    }

    public static boolean isBytes(ObjectField m) {
        return m != null && ObjectType.BYTES.equals(m.getType());
    }

    public static boolean isBoolean(ObjectField m) {
        return m != null && ObjectType.BOOLEAN.equals(m.getType());
    }

    public static boolean isObjectArray(ObjectField m) {
        return m != null && ObjectType.OBJECT_ARRAY.equals(m.getType());
    }

    public static boolean isObjectElement(ObjectField m) {
        return m != null && ObjectType.OBJECT_ELEMENT.equals(m.getType());
    }

    public static Number getNumber(ObjectField m) {
        return isNumber(m) ? (Number) m.getValue() : null;
    }

    public static String getString(ObjectField m) {
        return isString(m) ? (String) m.getValue() : null;
    }

    public static byte[] getBytes(ObjectField m) {
        return isBytes(m) ? (byte[]) m.getValue() : null;
    }

    public static Boolean getBoolean(ObjectField m) {
        return isBoolean(m) ? (Boolean) m.getValue() : null;
    }

    public static ObjectArray getObjectArray(ObjectField m) {
        return isObjectArray(m) ? (ObjectArray) m.getValue() : null;
    }

    public static String toString(ObjectField m) {
        if (m == null || m.getValue() == null)
            return "";
        String result;
        switch (m.getType()) {
            case STRING:
                result = (String) m.getValue();
                break;
            case BYTES:
                result = Base64.getEncoder().encodeToString((byte[]) m.getValue());
                break;
            default:
                result = m.getValue().toString();
                break;
        }
        return result;
    }

    public static Boolean toBoolean(ObjectField m) {
        if (m == null || m.getValue() == null)
            return false;
        Boolean result;
        switch (m.getType()) {
            case STRING:
                result = Boolean.parseBoolean((String) m.getValue());
                break;
            case BOOLEAN:
                result = (Boolean) m.getValue();
                break;
            case BYTES:
            case OBJECT_ARRAY:
                result = true;
                break;
            default:
                result = ((Number) m.getValue()).intValue() > 0;
                break;
        }
        return result;
    }

    public static Number toNumber(ObjectField m) {
        if (m == null || m.getValue() == null)
            return 0;
        Number result;
        switch (m.getType()) {
            case STRING: {
                String value = (String) m.getValue();
                if (!value.isBlank()) {
                    try {
                        if (value.contains(".")) {
                            result = Double.parseDouble(value);
                        } else {
                            result = Long.parseLong(value);
                        }
                    } catch (Exception e) {
                        result = 0;
                    }
                } else {
                    result = 0;
                }
                break;
            }
            case BOOLEAN:
                result = (Boolean) m.getValue() ? 1 : 0;
                break;
            case BYTES:
                result = ((byte[]) m.getValue()).length;
                break;
            case OBJECT_ARRAY:
                result = ((ObjectArray) m.getValue()).size();
                break;
            default:
                result = (Number) m.getValue();
                break;
        }
        return result;
    }

    public static Number toNumber(Number number, Class<? extends Number> cls) {
        if (number == null || cls == null)
            return 0;
        if (Byte.class.equals(cls)) {
            return number.byteValue();
        } else if (Short.class.equals(cls)) {
            return number.shortValue();
        } else if (Integer.class.equals(cls)) {
            return number.intValue();
        } else if (Long.class.equals(cls)) {
            return number.longValue();
        } else if (Float.class.equals(cls)) {
            return number.floatValue();
        } else if (Double.class.equals(cls)) {
            return number.doubleValue();
        } else if (BigInteger.class.equals(cls)) {
            return BigInteger.valueOf(number.longValue());
        } else if (BigDecimal.class.equals(cls)) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return number;
    }

    public static ObjectElement getObjectElement(ObjectField m) {
        if (m == null || m.getValue() == null)
            return null;
        if (m.getType() == ObjectType.OBJECT_ARRAY) {
            ObjectArray objectArray = (ObjectArray) m.getValue();
            if (objectArray.size() > 0 && objectArray.getType() == ObjectType.OBJECT_ELEMENT)
                return (ObjectElement) objectArray.get(0);
        } else if (m.getType() == ObjectType.OBJECT_ELEMENT) {
            return (ObjectElement) m.getValue();
        }
        return null;
    }

    public static List<ObjectElement> getObjectElements(ObjectField m) {
        if (m == null || m.getValue() == null)
            return null;
        if (m.getType() == ObjectType.OBJECT_ARRAY) {
            ObjectArray objectArray = (ObjectArray) m.getValue();
            if (objectArray.getType() == ObjectType.OBJECT_ELEMENT) {
                List<ObjectElement> result = new ArrayList<>(objectArray.size() + 1);
                for (int i = 0; i < objectArray.size(); i++)
                    result.add((ObjectElement) objectArray.get(i));
                return result;
            }
        } else if (m.getType() == ObjectType.OBJECT_ELEMENT) {
            return new ArrayList<>(List.of((ObjectElement) m.getValue()));
        }
        return null;
    }

    public static ObjectArray toObjectArray(ObjectField m) {
        if (m == null || m.getValue() == null)
            return new ObjectArray();
        ObjectArray objectArray = null;
        switch (m.getType()) {
            case VALUE_ANY:
            case STRING:
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case BIG_INTEGER:
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
            case BYTES:
            case BOOLEAN:
                objectArray = new ObjectArray(List.of(m.getValue()), m.getType());
                break;
            case OBJECT_ARRAY:
                objectArray = (ObjectArray) m.getValue();
                break;
            case OBJECT_ELEMENT:
                objectArray = new ObjectArray((ObjectElement) m.getValue());
                break;
        }
        return objectArray != null ? objectArray : new ObjectArray();
    }

    public static ObjectElement toObjectElement(ObjectField m) {
        if (m == null || m.getValue() == null)
            return new ObjectElement();
        ObjectElement objectElement = null;
        switch (m.getType()) {
            case VALUE_ANY:
            case STRING:
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case BIG_INTEGER:
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
            case BYTES:
            case BOOLEAN: {
                ObjectField objectField = new ObjectField("0");
                objectField.setValue(m.getType(), m.getValue());
                objectElement = new ObjectElement(List.of(objectField));
                break;
            }
            case OBJECT_ARRAY: {
                ObjectArray objectArray = (ObjectArray) m.getValue();
                if (isArrayContainObjectElements(objectArray)) {
                    objectElement = (ObjectElement) objectArray.get(0);
                } else if (objectArray.isSimple() && objectArray.size() > 0) {
                    objectElement = new ObjectElement();
                    for (int i = 0; i < objectArray.size(); i++)
                        objectElement.getFields().add(new ObjectField(String.valueOf(i), objectArray.getType(), objectArray.get(i)));
                }
                break;
            }
            case OBJECT_ELEMENT:
                objectElement = (ObjectElement) m.getValue();
                break;
        }
        return objectElement != null ? objectElement : new ObjectElement();
    }

    public static boolean isSameFields(ObjectArray objectArray) {
        if (objectArray == null || objectArray.getType() != ObjectType.OBJECT_ELEMENT)
            return false;
        boolean isSame = true;
        List<String> fieldNames = null;
        List<ObjectType> fieldType = null;
        for (int i = 0; i < objectArray.size(); i++) {
            ObjectElement objectElement = (ObjectElement) objectArray.get(i);
            if (i == 0) {
                fieldNames = objectElement.getFields().stream().map(ObjectField::getName).collect(Collectors.toList());
                fieldType = objectElement.getFields().stream().map(ObjectField::getType).collect(Collectors.toList());
            } else if (!objectElement.getFields().stream().map(ObjectField::getName).collect(Collectors.toList()).equals(fieldNames) ||
                    !objectElement.getFields().stream().map(ObjectField::getType).collect(Collectors.toList()).equals(fieldType)) {
                isSame = false;
                break;
            }
        }
        return isSame;
    }

    public static List<List<ObjectField>> findFields(ObjectArray objectArray, List<String> fieldPaths) {
        if (objectArray == null || objectArray.getType() != ObjectType.OBJECT_ELEMENT || fieldPaths == null || fieldPaths.isEmpty())
            return List.of();
        return fieldPaths.stream()
                .map(p -> {
                    String[] names = splitFieldNames(p);
                    List<ObjectElement> objectElements = new ArrayList<>(objectArray.size());
                    for (int i = 0; i < objectArray.size(); i++)
                        objectElements.add((ObjectElement) objectArray.get(i));
                    return findFields(objectElements, names, 0);
                })
                .collect(Collectors.toList());
    }

    public static List<ObjectField> findFields(List<ObjectElement> objectElements, String[] names, int level) {
        if (names.length <= level)
            return List.of();
        String name = names[level];
        List<ObjectField> fields = new LinkedList<>();
        for (ObjectElement objectElement : objectElements) {
            Optional<ObjectField> field = objectElement.findField(name);
            if (field.isPresent()) {
                ObjectField objectField = field.get();
                if (names.length > level + 1) {
                    List<ObjectElement> innerObjectElements = getObjectElements(objectField);
                    if (innerObjectElements != null)
                        fields.addAll(findFields(innerObjectElements, names, level + 1));
                } else {
                    fields.add(objectField);
                }
            }
        }
        return new ArrayList<>(fields);
    }

    public static String[] splitFieldNames(String fieldPath) {
        return fieldPath.split("\\.");
    }

    public static boolean isArrayContainNumber(ObjectArray objectArray) {
        return objectArray != null && objectArray.size() > 0 && (ObjectType.VALUE_ANY.equals(objectArray.getType()) || ObjectType.BYTE.equals(objectArray.getType()) || ObjectType.SHORT.equals(objectArray.getType()) || ObjectType.INTEGER.equals(objectArray.getType()) || ObjectType.LONG.equals(objectArray.getType()) || ObjectType.FLOAT.equals(objectArray.getType()) || ObjectType.DOUBLE.equals(objectArray.getType()) || ObjectType.BIG_INTEGER.equals(objectArray.getType()) || ObjectType.BIG_DECIMAL.equals(objectArray.getType()));
    }

    public static boolean isArrayContainString(ObjectArray objectArray) {
        return objectArray != null && objectArray.size() > 0 && (ObjectType.VALUE_ANY.equals(objectArray.getType()) || ObjectType.STRING.equals(objectArray.getType()));
    }

    public static boolean isArrayContainBytes(ObjectArray objectArray) {
        return objectArray != null && objectArray.size() > 0 && (ObjectType.VALUE_ANY.equals(objectArray.getType()) || ObjectType.BYTES.equals(objectArray.getType()));
    }

    public static boolean isArrayContainBoolean(ObjectArray objectArray) {
        return objectArray != null && objectArray.size() > 0 && (ObjectType.VALUE_ANY.equals(objectArray.getType()) || ObjectType.BOOLEAN.equals(objectArray.getType()));
    }

    public static ValueType toValueType(ObjectField m) {
        ValueType result = null;
        if (m == null || !m.isSimple())
            return result;
        if (m.getType() == ObjectType.VALUE_ANY) {
            result = getValueTypeObject(m.getValue());
        } else {
            result = ValueType.valueOf(m.getType().name());
        }
        return result;
    }

    public static ValueType getValueTypeObject(Object value) {
        if (value == null)
            return null;
        if (value instanceof Byte) {
            return ValueType.BYTE;
        } else if (value instanceof Short) {
            return ValueType.SHORT;
        } else if (value instanceof Integer) {
            return ValueType.INTEGER;
        } else if (value instanceof Long) {
            return ValueType.LONG;
        } else if (value instanceof Float) {
            return ValueType.FLOAT;
        } else if (value instanceof Double) {
            return ValueType.DOUBLE;
        } else if (value instanceof BigInteger) {
            return ValueType.BIG_INTEGER;
        } else if (value instanceof BigDecimal) {
            return ValueType.BIG_DECIMAL;
        } else if (value instanceof String) {
            return ValueType.STRING;
        } else if (value instanceof byte[]) {
            return ValueType.BYTES;
        } else if (value instanceof Boolean) {
            return ValueType.BOOLEAN;
        } else if (value instanceof ObjectArray) {
            return ValueType.OBJECT_ARRAY;
        } else {
            return null;
        }
    }

    public static ValueType getValueTypeClass(Class<?> cls) {
        if (cls == null)
            return null;
        if (Byte.class.isAssignableFrom(cls)) {
            return ValueType.BYTE;
        } else if (Short.class.isAssignableFrom(cls)) {
            return ValueType.SHORT;
        } else if (Integer.class.isAssignableFrom(cls)) {
            return ValueType.INTEGER;
        } else if (Long.class.isAssignableFrom(cls)) {
            return ValueType.LONG;
        } else if (Float.class.isAssignableFrom(cls)) {
            return ValueType.FLOAT;
        } else if (Double.class.isAssignableFrom(cls)) {
            return ValueType.DOUBLE;
        } else if (BigInteger.class.isAssignableFrom(cls)) {
            return ValueType.BIG_INTEGER;
        } else if (BigDecimal.class.isAssignableFrom(cls)) {
            return ValueType.BIG_DECIMAL;
        } else if (String.class.isAssignableFrom(cls)) {
            return ValueType.STRING;
        } else if (byte[].class.isAssignableFrom(cls)) {
            return ValueType.BYTES;
        } else if (Boolean.class.isAssignableFrom(cls)) {
            return ValueType.BOOLEAN;
        } else if (ObjectArray.class.isAssignableFrom(cls)) {
            return ValueType.OBJECT_ARRAY;
        } else {
            return null;
        }
    }

    public static ObjectType getObjectType(Object value) {
        if (value instanceof ObjectElement) {
            return ObjectType.OBJECT_ELEMENT;
        } else {
            return convertTo(getValueTypeObject(value));
        }
    }

    public static ObjectType convertTo(ValueType type) {
        if (type == null)
            return null;
        switch (type) {
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
            case BIG_INTEGER:
                return ObjectType.BIG_INTEGER;
            case FLOAT:
                return ObjectType.FLOAT;
            case DOUBLE:
                return ObjectType.DOUBLE;
            case BIG_DECIMAL:
                return ObjectType.BIG_DECIMAL;
            case BYTES:
                return ObjectType.BYTES;
            case BOOLEAN:
                return ObjectType.BOOLEAN;
            case OBJECT_ARRAY:
                return ObjectType.OBJECT_ARRAY;
        }
        return null;
    }

    public static ValueType convertTo(ObjectType type) {
        if (type == null)
            return null;
        switch (type) {
            case STRING:
                return ValueType.STRING;
            case BYTE:
                return ValueType.BYTE;
            case SHORT:
                return ValueType.SHORT;
            case INTEGER:
                return ValueType.INTEGER;
            case LONG:
                return ValueType.LONG;
            case BIG_INTEGER:
                return ValueType.BIG_INTEGER;
            case FLOAT:
                return ValueType.FLOAT;
            case DOUBLE:
                return ValueType.DOUBLE;
            case BIG_DECIMAL:
                return ValueType.BIG_DECIMAL;
            case BYTES:
                return ValueType.BYTES;
            case BOOLEAN:
                return ValueType.BOOLEAN;
            case OBJECT_ARRAY:
                return ValueType.OBJECT_ARRAY;
        }
        return null;
    }

    public static long executeParallel(ExecutionContextTool executionContextTool, int ecId, List<Object> params, long maxWorkIntervalMs, int sleepTimeMs) {
        long threadId = executionContextTool.getFlowControlTool().executeParallel(CommandType.EXECUTE, List.of(ecId), params, 0, maxWorkIntervalMs > 0 ? (int) (maxWorkIntervalMs / 1000) : 0);
        waitThread(executionContextTool, threadId, sleepTimeMs);
        return threadId;
    }

    public static long executeParallel(ExecutionContextTool executionContextTool, int ecId, List<Object> params) {
        return executeParallel(executionContextTool, ecId, params, 0, 50);
    }

    public static void waitThread(ExecutionContextTool executionContextTool, long threadId, int sleepTime) {
        if (sleepTime < 1)
            sleepTime = 1;
        do {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
            }
        } while (!executionContextTool.isNeedStop() && executionContextTool.getFlowControlTool().isThreadActive(threadId));
    }

    public static void waitThreadAndRelease(ExecutionContextTool executionContextTool, long threadId, int sleepTime) {
        waitThread(executionContextTool, threadId, sleepTime);
        executionContextTool.getFlowControlTool().releaseThread(threadId);
    }

    public static Optional<IAction> getFirstActionWithData(List<IAction> actions) {
        return actions.stream()
                .filter(ModuleUtils::hasData)
                .findFirst();
    }

    public static Optional<IAction> getFirstActionWithDataFromCommands(List<ICommand> commands) {
        return getFirstActionWithData(commands.stream()
                .flatMap(c -> c.getActions().stream())
                .collect(Collectors.toList()));
    }

    public static Optional<IAction> getLastActionWithData(List<IAction> actions) {
        return actions.stream()
                .filter(ModuleUtils::hasData)
                .reduce((first, second) -> second);
    }

    public static Optional<IAction> getLastActionWithDataFromCommands(List<ICommand> commands) {
        return getLastActionWithData(commands.stream()
                .flatMap(c -> c.getActions().stream())
                .collect(Collectors.toList()));
    }

    public static Optional<IAction> getFirstActionExecuteWithMessages(List<IAction> actions) {
        return actions.stream()
                .filter(a -> a.getType() == ActionType.EXECUTE && !a.getMessages().isEmpty())
                .findFirst();
    }

    public static Optional<IAction> getFirstActionExecuteWithMessagesFromCommands(List<ICommand> commands) {
        return getFirstActionExecuteWithMessages(commands.stream()
                .flatMap(c -> c.getActions().stream())
                .collect(Collectors.toList()));
    }

    public static Optional<IAction> getLastActionExecuteWithMessages(List<IAction> actions) {
        return actions.stream()
                .filter(a -> a.getType() == ActionType.EXECUTE && !a.getMessages().isEmpty())
                .reduce((first, second) -> second);
    }

    public static Optional<IAction> getLastActionExecuteWithMessagesFromCommands(List<ICommand> commands) {
        return getLastActionExecuteWithMessages(commands.stream()
                .flatMap(c -> c.getActions().stream())
                .collect(Collectors.toList()));
    }

    public static Optional<ObjectArray> getElements(List<IAction> actions) {
        return getFirstActionWithData(actions)
                .map(a -> ModuleUtils.deserializeToObject(new LinkedList<>(a.getMessages())))
                .filter(ModuleUtils::isArrayContainObjectElements);
    }

    public static Optional<ObjectElement> getElement(List<IAction> actions) {
        return getElements(actions).map(a -> (ObjectElement) a.get(0));
    }

    public static Optional<ObjectElement> executeAndGetElement(ExecutionContextTool executionContextTool, int id, List<Object> params) {
        return getElement(executeAndGet(executionContextTool, id, params));
    }

    public static Optional<ObjectArray> executeAndGetArrayElements(ExecutionContextTool executionContextTool, int id, List<Object> params) {
        return getElements(executeAndGet(executionContextTool, id, params));
    }

    public static <T> Optional<T> executeAndGetObject(ExecutionContextTool executionContextTool, int id, List<Object> params, Class<T> cls, boolean ignoreCaseInName) {
        return getElement(executeAndGet(executionContextTool, id, params))
                .map(e -> convertFromObjectElement(e, cls, true, ignoreCaseInName, null));
    }

    public static <T> Optional<List<T>> executeAndGetObjects(ExecutionContextTool executionContextTool, int id, List<Object> params, Class<T> cls, boolean ignoreCaseInName) {
        return getElements(executeAndGet(executionContextTool, id, params))
                .map(e -> convertFromObjectArray(e, cls, true, ignoreCaseInName))
                .filter(l -> !l.isEmpty());
    }

    public static Optional<List<IMessage>> executeAndGetMessages(ExecutionContextTool executionContextTool, int id, List<Object> params) {
        return executeAndGet(executionContextTool, id, params).stream()
                .filter(ModuleUtils::hasData)
                .map(IAction::getMessages)
                .findFirst();
    }

    public static List<IAction> executeAndGet(ExecutionContextTool executionContextTool, int id, List<Object> params) {
        executionContextTool.getFlowControlTool().executeNow(CommandType.EXECUTE, id, params);
        return executionContextTool.getFlowControlTool().getMessagesFromExecuted(id);
    }

    public static Optional<ObjectElement> executeParallelAndGetElement(ExecutionContextTool executionContextTool, int id, List<Object> params) {
        return getElement(executeParallelAndGet(executionContextTool, id, params));
    }

    public static Optional<ObjectArray> executeParallelAndGetArrayElements(ExecutionContextTool executionContextTool, int id, List<Object> params) {
        return getElements(executeParallelAndGet(executionContextTool, id, params));
    }

    public static <T> Optional<T> executeParallelAndGetObject(ExecutionContextTool executionContextTool, int id, List<Object> params, Class<T> cls, boolean ignoreCaseInName) {
        return getElement(executeParallelAndGet(executionContextTool, id, params))
                .map(e -> convertFromObjectElement(e, cls, true, ignoreCaseInName, null));
    }

    public static <T> Optional<List<T>> executeParallelAndGetObjects(ExecutionContextTool executionContextTool, int id, List<Object> params, Class<T> cls, boolean ignoreCaseInName) {
        return getElements(executeParallelAndGet(executionContextTool, id, params))
                .map(e -> convertFromObjectArray(e, cls, true, ignoreCaseInName))
                .filter(l -> !l.isEmpty());
    }

    public static Optional<List<IMessage>> executeParallelAndGetMessages(ExecutionContextTool executionContextTool, int id, List<Object> params) {
        return executeParallelAndGet(executionContextTool, id, params).stream()
                .filter(ModuleUtils::hasData)
                .map(IAction::getMessages)
                .findFirst();
    }

    public static List<IAction> executeParallelAndGet(ExecutionContextTool executionContextTool, int id, List<Object> params) {
        long threadId = executeParallel(executionContextTool, id, params);
        List<IAction> data = executionContextTool.getFlowControlTool().getMessagesFromExecuted(threadId, id);
        executionContextTool.getFlowControlTool().releaseThread(threadId);
        return data;
    }

    public static String getStackTraceAsString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public static String getErrorMessageOrClassName(Throwable t) {
        return t != null && t.getMessage() != null && !t.getMessage().isEmpty() ? t.getMessage() : (t != null ? t.getClass().getName() : "");
    }

    public static List<List<List<IMessage>>> getMessages(ExecutionContextTool executionContextTool) {
        return Stream.iterate(0, n -> n + 1)
                .limit(executionContextTool.countSource())
                .map(executionContextTool::getMessages)
                .map(l -> l.stream()
                        // .filter(a -> ActionType.EXECUTE.equals(a.getType()))
                        .map(IAction::getMessages)
                        .filter(l2 -> !l2.isEmpty())
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    public static List<List<IMessage>> getLestMessages(ExecutionContextTool executionContextTool) {
        return Stream.iterate(0, n -> n + 1)
                .limit(executionContextTool.countSource())
                .map(executionContextTool::getMessages)
                .map(l -> {
                    if (!l.isEmpty()) {
                        IAction iAction = l.get(l.size() - 1);
                        if (hasData(iAction))
                            return new ArrayList<>(iAction.getMessages());
                    }
                    return new ArrayList<IMessage>();
                })
                .collect(Collectors.toList());
    }

    public static List<IMessage> getMessagesJoin(ExecutionContextTool executionContextTool) {
        return Stream.iterate(0, n -> n + 1)
                .limit(executionContextTool.countSource())
                .flatMap(i -> executionContextTool.getMessages(i).stream())
                // .filter(ModuleUtils::hasData)
                .flatMap(a -> a.getMessages().stream())
                .collect(Collectors.toList());
    }

    public static void processMessages(ConfigurationTool configurationTool, ExecutionContextTool executionContextTool, CheckedConsumer<LinkedList<IMessage>> func) {
        for (int i = 0; i < executionContextTool.countSource(); i++)
            processMessages(configurationTool, executionContextTool, i, func);
    }

    public static void processMessages(ConfigurationTool configurationTool, ExecutionContextTool executionContextTool, int id, CheckedConsumer<LinkedList<IMessage>> func) {
        if (executionContextTool.countSource() > id) {
            executionContextTool.getMessages(id).forEach(a -> executor(configurationTool, executionContextTool, id, new LinkedList<>(a.getMessages()), func));
        } else {
            executor(configurationTool, executionContextTool, id, null, func);
        }
    }

    public static <T> void executor(ConfigurationTool configurationTool, ExecutionContextTool executionContextTool, int id, T messages, CheckedConsumer<T> func) {
        try {
            func.accept(id, messages);
        } catch (Exception e) {
            executionContextTool.addError(ModuleUtils.getErrorMessageOrClassName(e));
            configurationTool.loggerWarn(ModuleUtils.getStackTraceAsString(e));
        }
    }

    public static void processMessagesAll(ConfigurationTool configurationTool, ExecutionContextTool executionContextTool, CheckedConsumer<List<LinkedList<IMessage>>> func) {
        ArrayList<LinkedList<IMessage>> data = new ArrayList<>(executionContextTool.countSource());
        for (int i = 0; i < executionContextTool.countSource(); i++)
            data.add(getLastActionWithData(executionContextTool.getMessages(i)).map(IAction::getMessages).map(LinkedList::new).orElse(new LinkedList<>()));
        executor(configurationTool, executionContextTool, -1, data, func);
    }

    public static List<ObjectElement> toList(ObjectArray objectArray) {
        if (!isArrayContainObjectElements(objectArray))
            return List.of();
        List<ObjectElement> list = new ArrayList<>(objectArray.size());
        for (int i = 0; i < objectArray.size(); i++)
            list.add((ObjectElement) objectArray.get(i));
        return list;
    }

    public static <T> List<T> convertFromObjectArray(ObjectArray objectArray, Class<T> resultClass, boolean silent, boolean ignoreCaseInName) {
        List<T> result = new ArrayList<>();
        if (objectArray == null || objectArray.size() == 0)
            return result;
        try {
            if (objectArray.isSimple()) {
                result = toList(objectArray).stream()
                        .filter(o -> resultClass.isAssignableFrom(o.getClass()) || resultClass.isInstance(String.class))
                        .map(o -> resultClass.isInstance(String.class) ? (T) o.toString() : (T) o)
                        .collect(Collectors.toList());
            } else if (isArrayContainArrays(objectArray) && List.class.isAssignableFrom(resultClass)) {
                result = new ArrayList<>(objectArray.size());
                for (int i = 0; i < objectArray.size(); i++) {
                    ObjectArray objectArrayElement = (ObjectArray) objectArray.get(i);
                    if (objectArrayElement.isSimple()) {
                        result.add((T) Stream.iterate(0, n -> n + 1)
                                .limit(objectArrayElement.size())
                                .map(objectArrayElement::get)
                                .collect(Collectors.toList()));
                    }
                }
            } else if (isArrayContainObjectElements(objectArray)) {
                List<ObjectElementDescriptor<T>> propertyDescriptors = buildPropertyDescriptors(resultClass);
                result = toList(objectArray).stream()
                        .map(o -> convertFromObjectElement(o, resultClass, silent, ignoreCaseInName, propertyDescriptors))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            if (!silent)
                throw new RuntimeException(e);
        }
        return result;
    }

    private static <T> List<ObjectElementDescriptor<T>> buildPropertyDescriptors(Class<T> cls) throws IntrospectionException {
        BeanInfo info = Introspector.getBeanInfo(cls);
        List<ObjectElementDescriptor<T>> orderedList = new LinkedList<>();
        List<ObjectElementDescriptor<T>> unorderList = new LinkedList<>();
        Arrays.stream(info.getPropertyDescriptors())
                .forEach(pd -> {
                    try {
                        ObjectElementDescriptor<T> objectElementDescriptor = new ObjectElementDescriptor<>(cls, pd);
                        if (objectElementDescriptor.getSmcField() != null && objectElementDescriptor.getSmcField().order() > 0) {
                            orderedList.add(objectElementDescriptor);
                        } else {
                            unorderList.add(objectElementDescriptor);
                        }
                    } catch (Exception ignore) {
                    }
                });
        LinkedList<ObjectElementDescriptor<T>> result = new LinkedList<>();
        orderedList.stream()
                .sorted(Comparator.comparing(d -> d.getSmcField().order()))
                .forEach(result::add);
        result.addAll(unorderList);
        return result;
    }

    public static <T> T convertFromObjectElement(ObjectElement objectElement, Class<T> resultClass, boolean silent, boolean ignoreCaseInName) {
        return convertFromObjectElement(objectElement, resultClass, silent, ignoreCaseInName, null);
    }

    private static <T> T convertFromObjectElement(ObjectElement objectElement, Class<T> resultClass, boolean silent, boolean ignoreCaseInName,
                                                  List<ObjectElementDescriptor<T>> propertyDescriptors) {
        T result = null;
        if (objectElement == null)
            return result;
        try {
            T t = resultClass.getConstructor().newInstance();
            if (propertyDescriptors == null)
                propertyDescriptors = buildPropertyDescriptors(resultClass);
            for (ObjectElementDescriptor<T> descriptor : propertyDescriptors) {
                ObjectField f = ignoreCaseInName ? objectElement.findFieldIgnoreCase(descriptor.getName()).orElse(null) : objectElement.findField(descriptor.getName()).orElse(null);
                PropertyDescriptor p = descriptor.getPropertyDescriptor();
                if (f == null || p == null || p.getWriteMethod() == null) {
                    if (descriptor.getSmcField() != null && descriptor.getSmcField().required())
                        throw new RuntimeException(String.format("Field %s is required", descriptor.getName()));
                    continue;
                }
                String setter = p.getWriteMethod().getName();
                Class<?> propertyType = p.getPropertyType();
                Object value = null;
                try {
                    Method setterMethod = resultClass.getDeclaredMethod(setter, propertyType);
                    value = f.getValue();
                    if (value != null) {
                        if (descriptor.getSmcConverter() != null) {
                            value = descriptor.getSmcConverter().to(f, objectElement);
                        } else if (!propertyType.isAssignableFrom(value.getClass())) {
                            if (propertyType.equals(Boolean.class)) {
                                value = toBoolean(f);
                            } else if (propertyType.equals(String.class)) {
                                value = toString(f);
                            } else if (List.class.isAssignableFrom(propertyType)) {
                                ParameterizedType pType = (ParameterizedType) p.getReadMethod().getGenericReturnType();
                                Class<?> pClass = (Class<?>) pType.getActualTypeArguments()[0];
                                value = convertFromObjectArray(toObjectArray(f), pClass, silent, ignoreCaseInName);
                            } else if (Number.class.isAssignableFrom(propertyType)) {
                                value = toNumber(toNumber(f), (Class<? extends Number>) propertyType);
                            } else if (!Object.class.equals(propertyType)) {
                                ValueType valueTypeClass = getValueTypeClass(propertyType);
                                if (valueTypeClass == null)
                                    value = convertFromObjectElement(getObjectElement(f), propertyType, silent, ignoreCaseInName, null);
                            }
                        }
                    }
                    if (value != null) {
                        setterMethod.invoke(t, value);
                    } else if (descriptor.getSmcField() != null && descriptor.getSmcField().required()) {
                        throw new RuntimeException(String.format("Field %s is required", descriptor.getName()));
                    }
                } catch (Exception e) {
                    if (!silent)
                        throw new RuntimeException(String.format("Field %s setter=%s, type=%s, valueType=%s", descriptor.getName(), setter, propertyType.getName(), (value != null ? value.getClass().getName() : "")), e);
                }
            }
            result = t;
        } catch (Exception e) {
            if (!silent)
                throw new RuntimeException(e);
        }
        return result;
    }

    public static <T> ObjectArray convertToObjectArray(List<T> t, Class<T> resultClass, boolean silent) {
        ObjectArray result = new ObjectArray();
        if (t == null)
            return result;
        try {
            if (List.class.isAssignableFrom(resultClass)) {
                ObjectArray objectArrayTmp = new ObjectArray(ObjectType.OBJECT_ARRAY);
                t.stream()
                        .map(o -> (List<Object>) o)
                        .filter(l -> !l.isEmpty())
                        .forEach(l -> {
                            Object o = l.get(0);
                            ObjectType objectType = convertTo(getValueTypeObject(o));
                            if (objectType != null && objectType != ObjectType.OBJECT_ARRAY)
                                objectArrayTmp.add(new ObjectArray(l, objectType));
                        });
                result = objectArrayTmp;
            } else {
                ObjectType objectType = convertTo(getValueTypeClass(resultClass));
                if (objectType != null && objectType != ObjectType.OBJECT_ARRAY) {
                    result = new ObjectArray((List<Object>) t, objectType);
                } else {
                    List<ObjectElementDescriptor<T>> propertyDescriptors = buildPropertyDescriptors(resultClass);
                    result = new ObjectArray(
                            t.stream()
                                    .map(o -> convertToObjectElement(o, silent, propertyDescriptors))
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList()),
                            ObjectType.OBJECT_ELEMENT);
                }
            }
        } catch (Exception e) {
            if (!silent)
                throw new RuntimeException(e);
        }
        return result;
    }

    private static <T> ObjectField convertFrom(SmcConverter<?> converter, String name, T v, ObjectElement objectElement) throws Exception {
        return ((SmcConverter<T>) converter).from(name, v, objectElement);
    }

    public static <T> ObjectElement convertToObjectElement(T t, boolean silent) {
        return convertToObjectElement(t, silent, null);
    }

    private static <T> ObjectElement convertToObjectElement(T t, boolean silent, List<ObjectElementDescriptor<T>> propertyDescriptors) {
        ObjectElement result = null;
        if (t == null)
            return result;
        try {
            ObjectElement objectElement = new ObjectElement();
            Class<?> resultClass = t.getClass();
            if (propertyDescriptors == null)
                propertyDescriptors = buildPropertyDescriptors((Class<T>) resultClass);
            for (ObjectElementDescriptor<T> descriptor : propertyDescriptors) {
                PropertyDescriptor p = descriptor.getPropertyDescriptor();
                if (p == null || p.getWriteMethod() == null) {
                    if (descriptor.getSmcField() != null && descriptor.getSmcField().required())
                        throw new RuntimeException(String.format("Field %s is required", descriptor.getName()));
                    continue;
                }
                String getter = p.getReadMethod().getName();
                Class<?> propertyType = p.getPropertyType();
                Object value = null;
                try {
                    Method getterMethod = resultClass.getDeclaredMethod(getter);
                    value = getterMethod.invoke(t);
                    ObjectField objectField = null;
                    if (value != null) {
                        if (descriptor.getSmcConverter() != null) {
                            objectField = convertFrom(descriptor.getSmcConverter(), descriptor.getName(), value, objectElement);
                        } else if (List.class.isAssignableFrom(propertyType)) {
                            ParameterizedType pType = (ParameterizedType) p.getReadMethod().getGenericReturnType();
                            Class<?> pClass = (Class<?>) pType.getActualTypeArguments()[0];
                            value = convertToObjectArray((List) value, pClass, silent);
                        } else {
                            ValueType valueTypeClass = getValueTypeClass(Object.class.equals(propertyType) ? value.getClass() : propertyType);
                            if (valueTypeClass == null) {
                                // if (!parameterType.equals(String.class) && !Number.class.isAssignableFrom(parameterType) && !byte[].class.isAssignableFrom(parameterType)) {
                                value = convertToObjectElement(value, silent, null);
                            }
                        }
                    }
                    if (objectField == null) {
                        if (value != null) {
                            ObjectType objectType = getObjectType(value);
                            if (objectType == null) {
                                objectType = ObjectType.STRING;
                                value = value.toString();
                            }
                            objectField = new ObjectField(descriptor.getName(), objectType, value);
                        } else {
                            if (descriptor.getSmcField() != null && descriptor.getSmcField().required()) {
                                throw new RuntimeException(String.format("Field %s is required", descriptor.getName()));
                            } else {
                                objectField = new ObjectField(descriptor.getName(), convertTo(getValueTypeClass(getterMethod.getReturnType())), null);
                            }
                        }
                    }
                    objectElement.getFields().add(objectField);
                } catch (NoSuchMethodException e) {
                    if (!Objects.equals(descriptor.getName(), "class") && !silent)
                        throw new RuntimeException(String.format("Field %s getter=%s, type=%s", descriptor.getName(), getter, propertyType.getName()), e);
                } catch (Exception e) {
                    if (!silent)
                        throw new RuntimeException(String.format("Field %s getter=%s, type=%s, valueType=%s", descriptor.getName(), getter, propertyType.getName(), (value != null ? value.getClass().getName() : "")), e);
                }
            }
            result = objectElement;
        } catch (Exception e) {
            if (!silent)
                throw new RuntimeException(e);
        }
        return result;
    }

    public static LinkedList<IMessage> toLinkedList(IAction action) {
        return new LinkedList<>(action.getMessages());
    }

    public static Optional<LinkedList<IMessage>> getLastActionWithDataList(List<IAction> actions) {
        return getLastActionWithData(actions).map(ModuleUtils::toLinkedList);
    }

    public static Optional<LinkedList<IMessage>> getFirstActionWithDataList(List<IAction> actions) {
        return getFirstActionWithData(actions).map(ModuleUtils::toLinkedList);
    }

    public static void executeTasksInSeparateThreadPool(ConfigurationTool configurationTool, ExecutionContextTool executionContextTool, int countThreads, List<Runnable> runnableList) {
        ExecutorService executorService = Executors.newFixedThreadPool(countThreads);
        runnableList.forEach(r -> executorService.execute(() -> {
            try {
                r.run();
            } catch (Exception e) {
                configurationTool.loggerWarn(ModuleUtils.getStackTraceAsString(e));
            }
        }));
        try {
            executorService.shutdown();
            while (!executorService.awaitTermination(1, TimeUnit.SECONDS) && !executionContextTool.isNeedStop())
                ;
        } catch (InterruptedException ignore) {
        } finally {
            if (!executorService.isShutdown() || !executorService.isTerminated()) {
                configurationTool.loggerDebug("Thread pool not stopped, execute shutdownNow");
                executorService.shutdownNow();
            }
        }
    }

}
