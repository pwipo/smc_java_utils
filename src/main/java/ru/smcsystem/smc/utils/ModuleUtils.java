package ru.smcsystem.smc.utils;

import ru.smcsystem.api.dto.IAction;
import ru.smcsystem.api.dto.ICommand;
import ru.smcsystem.api.dto.IMessage;
import ru.smcsystem.api.enumeration.ActionType;
import ru.smcsystem.api.enumeration.MessageType;
import ru.smcsystem.api.enumeration.ValueType;

import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

public class ModuleUtils {

    public static Number getNumber(IMessage m) {
        return isNumber(m) ? (Number) m.getValue() : null;
    }

    public static String getString(IMessage m) {
        return isString(m) ? (String) m.getValue() : null;
    }

    public static byte[] getBytes(IMessage m) {
        return isBytes(m) ? (byte[]) m.getValue() : null;
    }

    public static String toString(IMessage m) {
        if (m == null)
            return null;
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

    /**
     * deserialize messages to object array
     * use object serialization format
     * object serialization format:
     *      number - type of elements, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[]
     *      number - number of item in array
     *      items. depend of type. format:
     *          if item type is 0 (ObjectArray): then list of arrays. each has format described above, recursion.
     *          if item type is 1 (ObjectElement): then list of objects. each has format:
     *              number - number of fields in object
     *              list of fields. format:
     *                  string - field name.
     *                  number - field type, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[]
     *                  field value. depend of type. format:
     *                      if item type is 0-ObjectArray: then list of arrays. each has format described above, recursion.
     *                      if item type is 1-ObjectElement: then list of objects. each has format described above, recursion.
     *                      if item type is 2-ObjectElementSimple: then list of simple objects. each has format described above, recursion.
     *                      else: any type - simple value.
     *          if item type is 2 (ObjectElementSimple): then list of simple objects has format:
     *              number - number of fields in each object (one for all)
     *              list of each object fields. format:
     *                  string - field name.
     *                  any type - simple value.
     *          else: list of simple values, format:
     *              any type - simple value.
     *
     * @param messages - list of messages for deserialization
     * @return ObjectArray
     */
    public static ObjectArray deserializeToObject(LinkedList<IMessage> messages) {
        ObjectArray objectArray = new ObjectArray();

        IMessage message = messages.peek();
        Number typeId = getNumber(message);
        if (typeId == null)
            return objectArray;
        messages.poll();
        ObjectType type = ObjectType.values()[typeId.intValue()];

        message = messages.peek();
        Number size = getNumber(message);
        if (size == null)
            return objectArray;
        messages.poll();
        int count = size.intValue();

        objectArray = new ObjectArray(count, type);
        try {
            switch (type) {
                case OBJECT_ARRAY:
                    for (int i = 0; i < count; i++)
                        objectArray.add(deserializeToObject(messages));
                    break;
                case OBJECT_ELEMENT:
                    for (int i = 0; i < count; i++)
                        objectArray.add(deserializeToObjectElement(messages, -1));
                    break;
                case OBJECT_ELEMENT_SIMPLE: {
                    message = messages.peek();
                    Number countFields = getNumber(message);
                    if (countFields != null) {
                        messages.poll();
                        for (int i = 0; i < count; i++)
                            objectArray.add(deserializeToObjectElement(messages, countFields.intValue()));
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
                    for (int i = 0; i < count; i++)
                        objectArray.add(messages.poll().getValue());
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return objectArray;
    }

    private static ObjectElement deserializeToObjectElement(LinkedList<IMessage> messages, int countFields) {
        ObjectElement objectElement = new ObjectElement();

        IMessage message = null;
        int count = -1;
        if (countFields < 0) {
            message = messages.peek();
            Number size = getNumber(message);
            if (size == null)
                return objectElement;
            messages.poll();
            count = size.intValue();
        } else {

            count = countFields;
        }

        try {
            for (int i = 0; i < count; i++) {
                if ((countFields > -1 && messages.size() < 2) || (countFields < 0 && messages.size() < 3))
                    break;
                String fieldName = toString(messages.poll());

                ObjectType type = ObjectType.VALUE_ANY;
                if (countFields < 0) {
                    message = messages.peek();
                    Number typeId = getNumber(message);
                    if (typeId == null)
                        break;
                    messages.poll();
                    type = ObjectType.values()[typeId.intValue()];
                }

                switch (type) {
                    case OBJECT_ARRAY:
                        objectElement.getFields().add(new ObjectField(fieldName, deserializeToObject(messages)));
                        break;
                    case OBJECT_ELEMENT:
                        objectElement.getFields().add(new ObjectField(fieldName, deserializeToObjectElement(messages, -1)));
                        break;
                    case OBJECT_ELEMENT_SIMPLE: {
                        message = messages.peek();
                        Number countFields2 = getNumber(message);
                        if (countFields2 != null) {
                            messages.poll();
                            objectElement.getFields().add(new ObjectField(fieldName, deserializeToObjectElement(messages, countFields2.intValue())));
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
                        objectElement.getFields().add(new ObjectField(fieldName, messages.poll()));
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return objectElement;
    }

    /**
     * serialize objects to messages
     * use object serialization format
     * object serialization format:
     *      number - type of elements, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[]
     *      number - number of item in array
     *      items. depend of type. format:
     *          if item type is 0 (ObjectArray): then list of arrays. each has format described above, recursion.
     *          if item type is 1 (ObjectElement): then list of objects. each has format:
     *              number - number of fields in object.
     *              list of fields. format:
     *                  string - field name.
     *                  number - field type, may by: 0-ObjectArray, 1-ObjectElement, 2-ObjectElementSimple, 3-AnySimpleTypes, 4-String, 5-Byte, 6-Short, 7-Integer, 8-Long, 9-Float, 10-Double, 11-BigInteger, 12-BigDecimal, 13-byte[]
     *                  field value. depend of type. format:
     *                      if item type is 0-ObjectArray: then list of arrays. each has format described above, recursion.
     *                      if item type is 1-ObjectElement: then list of objects. each has format described above, recursion.
     *                      if item type is 2-ObjectElementSimple: then list of simple objects. each has format described above, recursion.
     *                      else: any type - simple value.
     *          if item type is 2 (ObjectElementSimple): then list of simple objects has format:
     *              number - number of fields in each object (one for all).
     *              list of each object fields. format:
     *                  string - field name.
     *                  any type - simple value.
     *          else: list of simple values, format:
     *              any type - simple value.
     *
     * @param mainList - list of objects for serialization
     * @return List of values, ready for send as messages
     */
    public static List<Object> serializeFromObject(ObjectArray mainList) {
        List<Object> result = new LinkedList<>();
        if (mainList == null)
            return result;
        result.add(mainList.getType().ordinal());
        result.add(mainList.size());
        if (mainList.size() == 0)
            return result;
        switch (mainList.getType()) {
            case OBJECT_ARRAY:
                for (int i = 0; i < mainList.size(); i++)
                    result.addAll(serializeFromObject((ObjectArray) mainList.get(i)));
                break;
            case OBJECT_ELEMENT:
                for (int i = 0; i < mainList.size(); i++)
                    result.addAll(serializeFromObject((ObjectElement) mainList.get(i), false));
                break;
            case OBJECT_ELEMENT_SIMPLE:
                result.add(((ObjectElement) mainList.get(0)).getFields().size());
                for (int i = 0; i < mainList.size(); i++)
                    result.addAll(serializeFromObject((ObjectElement) mainList.get(i), true));
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
            case BYTES: {
                for (int i = 0; i < mainList.size(); i++)
                    result.add(mainList.get(i));
                break;
            }
        }
        return result;
    }

    private static List<Object> serializeFromObject(ObjectElement objectElement, boolean isSimple) {
        List<Object> result = new LinkedList<>();
        if (objectElement == null)
            return result;
        if (!isSimple)
            result.add(objectElement.getFields().size());
        objectElement.getFields().forEach(objField -> {
            result.add(objField.getName());
            if (!isSimple)
                result.add(objField.getType().ordinal());
            switch (objField.getType()) {
                case OBJECT_ARRAY:
                    result.addAll(serializeFromObject((ObjectArray) objField.getValue()));
                    break;
                case OBJECT_ELEMENT:
                    result.addAll(serializeFromObject((ObjectElement) objField.getValue(), false));
                    break;
                case OBJECT_ELEMENT_SIMPLE:
                    result.add(((ObjectElement) objField.getValue()).getFields().size());
                    result.addAll(serializeFromObject((ObjectElement) objField.getValue(), true));
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
                    result.add(objField.getValue());
                    break;
            }
        });

        return result;
    }

    public static boolean isNumber(IMessage m) {
        return m != null && ((ValueType.BYTE.equals(m.getType()) || ValueType.SHORT.equals(m.getType()) || ValueType.INTEGER.equals(m.getType()) || ValueType.LONG.equals(m.getType()) || ValueType.FLOAT.equals(m.getType()) || ValueType.DOUBLE.equals(m.getType()) || ValueType.BIG_INTEGER.equals(m.getType()) || ValueType.BIG_DECIMAL.equals(m.getType())));
    }

    public static boolean isString(IMessage m) {
        return m != null && ValueType.STRING.equals(m.getType());
    }

    public static boolean isBytes(IMessage m) {
        return m != null && ValueType.BYTES.equals(m.getType());
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

    public static boolean isArrayContainObjectElements(ObjectArray objectArray) {
        return objectArray != null && objectArray.size() > 0 && (ObjectType.OBJECT_ELEMENT == objectArray.getType() || ObjectType.OBJECT_ELEMENT_SIMPLE == objectArray.getType());
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

    public static Number getNumber(ObjectField m) {
        return isNumber(m) ? (Number) m.getValue() : null;
    }

    public static String getString(ObjectField m) {
        return isString(m) ? (String) m.getValue() : null;
    }

    public static byte[] getBytes(ObjectField m) {
        return isBytes(m) ? (byte[]) m.getValue() : null;
    }

    public static String toString(ObjectField m) {
        if (m == null)
            return null;
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

    public static ObjectElement getObjectElement(ObjectField m) {
        if (m.getType() == ObjectType.OBJECT_ARRAY) {
            ObjectArray objectArray = (ObjectArray) m.getValue();
            if (objectArray.size() > 0 && (objectArray.getType() == ObjectType.OBJECT_ELEMENT || objectArray.getType() == ObjectType.OBJECT_ELEMENT_SIMPLE))
                return (ObjectElement) objectArray.get(0);
        } else if (m.getType() == ObjectType.OBJECT_ELEMENT || m.getType() == ObjectType.OBJECT_ELEMENT_SIMPLE) {
            return (ObjectElement) m.getValue();
        }
        return null;
    }

}
