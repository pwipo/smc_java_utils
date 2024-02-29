package ru.smcsystem.smc.utils;

import org.junit.Test;
import ru.smcsystem.api.dto.ObjectArray;
import ru.smcsystem.api.dto.ObjectElement;
import ru.smcsystem.api.dto.ObjectField;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ModuleUtilsTest {

    @Test
    public void convertFromObjectArray() {
        Date date = new Date();
        Instant now = Instant.ofEpochMilli(date.getTime() + 1);
        List<TestObject> testObjects = ModuleUtils.convertFromObjectArray(
                new ObjectArray(
                        new ObjectElement(
                                new ObjectField("name", "obj1"),
                                new ObjectField("value_1", 1),
                                new ObjectField("value_2", 2),
                                new ObjectField("value_3", 3),
                                new ObjectField("value_4", 4),
                                new ObjectField("value5", 5),
                                new ObjectField("date", date.getTime()),
                                new ObjectField("instant", now.toEpochMilli()),
                                new ObjectField("objType", "TWO"),
                                new ObjectField("map", new ObjectArray(
                                        new ObjectElement(new ObjectField("key1", "one"), new ObjectField("value1", "1")),
                                        new ObjectElement(new ObjectField("key1", "two"), new ObjectField("value1", "2")),
                                        new ObjectElement(new ObjectField("key2", "three"), new ObjectField("value2", "3"))
                                ))
                        ),
                        new ObjectElement(
                                new ObjectField("name", "obj2")
                        )),
                TestObject.class, true, false);
        assertEquals(testObjects.size(), 1);
        TestObject testObject = testObjects.get(0);
        assertEquals(testObject.getName(), "obj1");
        assertEquals(testObject.getValue1(), (Long) 1L);
        assertEquals(testObject.getValue2(), (Integer) 2);
        assertEquals(testObject.getValue3(), (Float) 3.f);
        assertEquals(testObject.getValue4(), BigDecimal.valueOf(4.));
        assertEquals(testObject.getValue5(), BigInteger.valueOf(5));
        assertEquals(testObject.getDate(), date);
        assertEquals(testObject.getInstant(), now);
        assertEquals(testObject.getObjType(), ObjType.TWO);
        assertEquals(testObject.getMap().size(), 2);
    }

    @Test
    public void convertToObjectArray() {
        Date date = new Date();
        Instant now = Instant.ofEpochMilli(date.getTime() + 1);
        TestObject testObject = new TestObject();
        testObject.setName("obj1");
        testObject.setValue1(1L);
        testObject.setValue2(2);
        testObject.setValue3(3.f);
        testObject.setValue4(BigDecimal.valueOf(4.));
        testObject.setValue5(BigInteger.valueOf(5));
        testObject.setDate(date);
        testObject.setInstant(now);
        testObject.setObjType(ObjType.THREE);
        testObject.setMap(Map.of("one", "1", "two", "2"));
        ObjectArray objectArray = ModuleUtils.convertToObjectArray(List.of(testObject), TestObject.class, false);
        assertEquals(objectArray.size(), 1);
        ObjectElement objectElement = (ObjectElement) objectArray.get(0);
        assertEquals(objectElement.getFields().size(), 10);
        System.out.println(objectElement);
        assertEquals(objectElement.getFields().get(0).getValue(), "obj1");
        assertEquals(objectElement.getFields().get(3).getValue(), (Long) 1L);
        assertEquals(objectElement.getFields().get(4).getValue(), (Integer) 2);
        assertEquals(objectElement.getFields().get(1).getValue(), (Float) 3.f);
        assertEquals(objectElement.getFields().get(2).getValue(), BigDecimal.valueOf(4.));
        assertEquals(objectElement.getFields().get(5).getValue(), date.getTime());
        assertEquals(objectElement.getFields().get(6).getValue(), now.toEpochMilli());
        assertEquals(((ObjectArray) objectElement.getFields().get(7).getValue()).size(), 2);
        assertEquals(objectElement.getFields().get(8).getValue(), ObjType.THREE.name());
        assertEquals(objectElement.getFields().get(9).getValue(), BigInteger.valueOf(5));
    }
}