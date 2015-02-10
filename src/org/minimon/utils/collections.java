package org.minimon.utils;

import com.sun.istack.internal.Nullable;
import org.ini4j.Ini;

import java.util.*;

/**
 * Дополнительные (возможно, велосипедные) реализации
 * функций по поиску и преобразованию коллекций, используемых в приложении
 */
public class collections {
    /**
     * Выполняет преобразование коллекций типа "Ini" во вложенный "LinkedHashMap<String, LinkedHashMap<String, String>>"
     * с учётом того, что все значения Ini-объекта - строковые, отсутствую подкатегории категорий.
     * Игнорируются пустые категории и пустые значения
     * Вышестоящая категория(уровень ini-файла) -- названия секций-секции
     * Нижестоящая категория(уровень секции) -- ключи-значения
     *
     * @param iniObject Объект ini-типа
     * @return Объект вложенного LinkedHashMap-типа
     */
    public static LinkedHashMap<String, LinkedHashMap<String, String>> IniToLinkedHashMap(Ini iniObject) {
        LinkedHashMap<String, LinkedHashMap<String, String>> comboMap = new LinkedHashMap<>();
        try {
            for (String item : iniObject.keySet()) {
                try {
                    comboMap.put(item, new LinkedHashMap<>(iniObject.get(item)));
                } catch (Exception subIgnore) {
                }
            }
        } catch (Exception ignore) {
        }

        Iterator<Map.Entry<String, LinkedHashMap<String, String>>> item = comboMap.entrySet().iterator();
        while (item.hasNext()) {
            Map.Entry<String, LinkedHashMap<String, String>> entry = item.next();
            Iterator<Map.Entry<String, String>> subItem = entry.getValue().entrySet().iterator();
            while (subItem.hasNext()) {
                Map.Entry<String, String> subEntry = subItem.next();
                // Стираем парные кавычки подзначений категорий
                if ((subEntry.getValue().startsWith("\"")) && (subEntry.getValue().endsWith("\""))) {
                    String buffer = subEntry.getValue();
                    subEntry.setValue(buffer.substring(1, buffer.length() - 1));
                }
                // Удаляем пустые подзначения
                if (subEntry.getValue().isEmpty())
                    subItem.remove();
            }
            // Удаляем пустые категории
            if (entry.getValue().size() == 0)
                item.remove();
        }

        return comboMap;
    }

    /**
     * Выполняет преобразование вложенных коллекций "LinkedHashMap<String, LinkedHashMap<String, String>>" в коллекции типа
     * "Ini".
     * Вышестоящая категория(уровень ini-файла) -- названия секций-секции
     * Нижестоящая категория(уровень секции) -- ключи-значения
     *
     * @param LinkedHashMapObject Объект вложенного LinkedHashMap-типа
     * @return Объект ini-типа
     */
    public static Ini LinkedHashMapToIni(LinkedHashMap<String, LinkedHashMap<String, String>> LinkedHashMapObject) {
        Ini iniData = new Ini();
        for (String item : LinkedHashMapObject.keySet()) {
            iniData.add(item).putAll(LinkedHashMapObject.get(item));
        }
        return iniData;
    }

    /**
     * Выполняет поиск в названиях ключей секций во вложенных коллекциях
     * без учёта регистра
     * Возвращает коллекцию найденного ключа
     *
     * @param comboMap     Вложенная коллекция, 1го уровня
     * @param searchPhrase Искомая фраза
     * @return Коллекция 2го уровня, либо null, если не найдено
     */
	@Nullable
    public static LinkedHashMap<String, String> searchKeyInComboIgnoreCase(LinkedHashMap<String, LinkedHashMap<String, String>> comboMap, String searchPhrase) {
        for (String item : comboMap.keySet()) {
            if (item.equalsIgnoreCase(searchPhrase))
                return comboMap.get(item);
        }
        return null;
    }

    /**
     * Выполняет поиск в названиях ключей секций во вложенных подколлекциях
     * без учёта регистра.
     * Возвращает строку найденного ключа
     *
     * @param subMap       Вложенная коллекция, 2го уровня
     * @param searchPhrase Искомая фраза
     * @return Строка либо null, если не найдено
     */
	@Nullable
    public static String searchKeyInSubIgnoreCase(LinkedHashMap<String, String> subMap, String searchPhrase) {
        for (String item : subMap.keySet()) {
            if (item.equalsIgnoreCase(searchPhrase))
                return subMap.get(item);
        }
        return null;
    }

    /**
     * Выполняет поиск во вложенных подколлекциях с указанием имени подколлекции
     * без учёта регистра
     *
     * @param comboMap    коллекция 1го уровня
     * @param sectionName имя секции, 2й уровень
     * @param keyName     имя ключа, уроверь секции
     * @return Значение ключа либо null
     */
	@Nullable
    public static String searchKeyInSubIgnoreCase(LinkedHashMap<String, LinkedHashMap<String, String>> comboMap, String sectionName, String keyName) {
        for (String item : comboMap.keySet()) {
            if (item.equalsIgnoreCase(sectionName)) {
                for (String subItem : comboMap.get(item).keySet()) {
                    if (subItem.equalsIgnoreCase(keyName))
                        return comboMap.get(item).get(subItem);
                }
            }
        }
        return null;
    }

    /**
     * Выполняет поиск во вложенных подколлекциях с указанием имени подколлекции
     * без учёта регистра
     *
     * @param comboMap     коллекция 1го уровня
     * @param sectionName  имя секции, 2й уровень
     * @param keyName      имя ключа, уроверь секции
     * @param defaultValue значение по-умолчанию
     * @return Значение ключа либо значение по-умолчанию
     */
    public static String searchKeyInSubIgnoreCase(LinkedHashMap<String, LinkedHashMap<String, String>> comboMap, String sectionName, String keyName, String defaultValue) {
        for (String item : comboMap.keySet()) {
            if (item.equalsIgnoreCase(sectionName)) {
                for (String subItem : comboMap.get(item).keySet()) {
                    if (subItem.equalsIgnoreCase(keyName))
                        return comboMap.get(item).get(subItem);
                }
            }
        }
        return defaultValue;
    }

    /**
     * Выполняет поиск в LinkedList без учёта регистра
     *
     * @param list Объект типа LinkedList
     * @return Найденное значение либо null
     */
	@Nullable
    public static String searchInLinkedList(LinkedList<String> list, String searchItem) {
        for (String item : list) {
            if (item.equalsIgnoreCase(searchItem))
                return item;
        }
        return null;
    }

    /**
     * Преобразует секцию в Properties, полностью копируя содержимое
     * Для применения в объектах, принимающих параметры в Properties (например JDBC)
     *
     * @param comboMap    коллекция 1го уровня
     * @param sectionName имя секции (без учёта регистра)
     * @return Properties с ключами и значениями категории, либо пустая Property
     * при отсутствии такой категории
     */
    public static Properties sectionToProperties(LinkedHashMap<String, LinkedHashMap<String, String>> comboMap, String sectionName) {
        Properties retValue = new Properties();
        LinkedHashMap<String, String> section = searchKeyInComboIgnoreCase(comboMap, sectionName);
        if (section != null) {
            if (section.size() == 0) {
                retValue.putAll(section);
            }
        }
        return retValue;
    }
}
