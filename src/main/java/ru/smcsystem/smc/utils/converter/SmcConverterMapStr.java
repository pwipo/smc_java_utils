package ru.smcsystem.smc.utils.converter;

import ru.smcsystem.api.dto.ObjectArray;
import ru.smcsystem.api.dto.ObjectElement;
import ru.smcsystem.api.dto.ObjectField;
import ru.smcsystem.api.enumeration.ObjectType;
import ru.smcsystem.smc.utils.ModuleUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SmcConverterMapStr extends SmcConverter<Map<String, String>> {

    @Override
    public Map<String, String> to(ObjectField field, ObjectElement objectElement) throws Exception {
        if (field.isSimple()) {
            return new HashMap<>(Map.of(field.getName(), field.getValue().toString()));
        } else if (ModuleUtils.isObjectElement(field)) {
            ObjectElement objectElementValue = ModuleUtils.getObjectElement(field);
            if (objectElementValue.isSimple())
                return objectElementValue.getFields().stream().filter(f -> f.getValue() != null).collect(Collectors.toMap(ObjectField::getName, f -> f.getValue().toString()));
        } else if (ModuleUtils.isObjectArray(field)) {
            ObjectArray objectArray = ModuleUtils.getObjectArray(field);
            HashMap<String, String> result = new HashMap<>(objectArray.size());
            if (ModuleUtils.isArrayContainObjectElements(objectArray)) {
                for (int i = 0; i < objectArray.size(); i++) {
                    ObjectElement objectElementInner = (ObjectElement) objectArray.get(i);
                    String key = objectElementInner.findFieldIgnoreCase("key").map(ModuleUtils::toString).orElse(null);
                    String value = objectElementInner.findFieldIgnoreCase("value").map(ModuleUtils::toString).orElse(null);
                    if (key != null && value != null)
                        result.put(key, value);
                }
            } else if (objectArray.isSimple()) {
                for (int i = 0; i < objectArray.size(); i++)
                    result.put(String.valueOf(i), objectArray.get(i).toString());
            }
            return result;
        }
        return null;
    }

    @Override
    public ObjectField from(String name, Map<String, String> v, ObjectElement objectElement) throws Exception {
        return new ObjectField(name, new ObjectArray(
                v.entrySet().stream().map(e -> new ObjectElement(
                        new ObjectField("key", e.getKey()), new ObjectField("value", e.getValue())
                )).collect(Collectors.toList()),
                ObjectType.STRING));
    }
}
