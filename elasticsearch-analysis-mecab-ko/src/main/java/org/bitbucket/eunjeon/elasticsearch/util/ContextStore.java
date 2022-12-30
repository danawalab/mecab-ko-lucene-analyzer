package org.bitbucket.eunjeon.elasticsearch.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ContextStore {
    private final static Map<Class<?>, ContextStore> STORE = new ConcurrentHashMap<>();
    private Map<String, Object> map;

    public final static ContextStore getStore(Class<?> cls) {
        if (cls != null) {
            if (STORE.containsKey(cls)) {
                return STORE.get(cls);
            } else {
                ContextStore store = new ContextStore();
                STORE.put(cls, store);
                return store;
            }
        }
        return null;
    }

    private ContextStore() {
        map = new ConcurrentHashMap<>();
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public void put(String key, Object obj) {
        map.put(key, obj);
    }

    public Double getDouble(String key, Double def) {
        Double ret = null;
        Object obj = map.get(key);
        if (obj != null) {
            if (obj instanceof String) {
                ret = Double.parseDouble((String) obj);
            } else {
                ret = def;
            }
        }
        return ret;
    }

    public Float getFloat(String key, Float def) {
        Float ret = null;
        Object obj = map.get(key);
        if (obj != null) {
            if (obj instanceof String) {
                ret = Float.parseFloat((String) obj);
            } else {
                ret = def;
            }
        }
        return ret;
    }

    public Long getLong(String key, Long def) {
        Long ret = null;
        Object obj = map.get(key);
        if (obj != null) {
            if (obj instanceof String) {
                ret = Long.parseLong((String) obj);
            } else {
                ret = def;
            }
        }
        return ret;
    }

    public Integer getInteger(String key, Integer def) {
        Integer ret = null;
        Object obj = map.get(key);
        if (obj != null) {
            if (obj instanceof String) {
                ret = Integer.parseInt((String) obj);
            } else {
                ret = def;
            }
        }
        return ret;
    }

    public String getString(String key, String def) {
        String ret = null;
        Object obj = map.get(key);
        if (obj != null) {
            ret = String.valueOf(obj);
        } else {
            ret = def;
        }
        return ret;
    }

    public <T> T getAs(String key, Class<T> cls) {
        T ret = null;
        Object obj = map.get(key);
        if (obj != null) {
            ret = (T) obj;
        }
        return ret;
    }
}