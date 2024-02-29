package ru.smcsystem.smc.utils.converter;

import ru.smcsystem.api.dto.ObjectArray;
import ru.smcsystem.api.dto.ObjectElement;
import ru.smcsystem.api.dto.ObjectField;
import ru.smcsystem.api.enumeration.ObjectType;
import ru.smcsystem.smc.utils.ModuleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SmcConverterListStr extends SmcConverter<List<String>> {

    @Override
    public List<String> to(ObjectField field, ObjectElement objectElement) throws Exception {
        if (field.isSimple()) {
            return new ArrayList<>(List.of(field.getValue().toString()));
        } else if (ModuleUtils.isObjectElement(field)) {
            return ModuleUtils.getObjectElement(field).getFields().stream()
                    .filter(ObjectField::isSimple)
                    .map(ObjectField::getValue)
                    .map(Object::toString).
                    collect(Collectors.toList());
        } else if (ModuleUtils.isObjectArray(field)) {
            ObjectArray objectArray = ModuleUtils.getObjectArray(field);
            if (objectArray != null && objectArray.isSimple()) {
                List<String> result = new ArrayList<>(objectArray.size());
                for (int i = 0; i < objectArray.size(); i++)
                    result.add(objectArray.get(i).toString());
                return result;
            }
        }
        return null;
    }

    @Override
    public ObjectField from(String name, List<String> v, ObjectElement objectElement) throws Exception {
        return new ObjectField(name, new ObjectArray((List) v, ObjectType.STRING));
    }

}
