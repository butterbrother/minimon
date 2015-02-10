package org.minimon.system;

import org.minimon.core.staticValues;

/**
 * Платформозависимые рализации и
 * платформоопределители
 */
public class cross
        implements staticValues {

    /**
     * Определяет и возвращает тип ОС
     * Числовые идентификаторы описаны в staticValues
     *
     * @return числовой идентификатор
     */
    static public int getSystemType() {
        String propertyOSVersion = System.getProperty("os.name").toLowerCase();
        if (propertyOSVersion.contains("linux"))
            return OS_TYPE_LINUX;
        if (propertyOSVersion.contains("windows"))
            return OS_TYPE_WINDOWS;
        if (propertyOSVersion.contains("sun"))
            return OS_TYPE_SUN;

        return OS_TYPE_UNKNOWN;
    }
}
