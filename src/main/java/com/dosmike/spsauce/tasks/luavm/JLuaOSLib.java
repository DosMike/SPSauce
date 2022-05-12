package com.dosmike.spsauce.tasks.luavm;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OsLib;
import org.luaj.vm2.lib.VarArgFunction;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/** heavily limited os lib that has no file access */
public class JLuaOSLib extends OsLib {

    public JLuaOSLib() {}

    private enum OsLibNames {
        clock(0),
        date(1),
        difftime(2),
        //execute(3),
        //exit(4),
        //getenv(5),
        //remove(6),
        //rename(7),
        //setlocale(8),
        time(9),
        //tmpname(10),
        ;
        private final int opcode;
        OsLibNames(int opcode) { this.opcode = opcode; }
        void register(OsLib lib, LuaTable table) {
            table.set(name(), reflectiveOsLibFunc(lib, opcode, name()));
        }
    };

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        globals = env.checkglobals();
        LuaTable os = new LuaTable();
        for (OsLibNames libfun : OsLibNames.values()) {
            libfun.register(this, os);
        }
        env.set("os", os);
        env.get("package").get("loaded").set("os", os);
        return os;
    }

    // yea, the implementation class is protected, but it's not like java really cares about that...

    private static final Constructor<VarArgFunction> OsLibFuncConstructor = getOsLibFuncConstructor();
    @SuppressWarnings("unchecked")
    private static Constructor<VarArgFunction> getOsLibFuncConstructor() {
        try {
            for (Class<?> inner : OsLib.class.getDeclaredClasses()) {
                if ("OsLibFunc".equals(inner.getSimpleName())) {
                    //inner classes transform the constructor similar to c++ member classes are transformed
                    Constructor<VarArgFunction> constructor = (Constructor<VarArgFunction>) inner.getDeclaredConstructor(OsLib.class, Integer.TYPE, String.class);
                    constructor.setAccessible(true);
                    return constructor;
                }
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not find OsLibFunc constructor, did the Lua library change?", e);
        }
        throw new RuntimeException("Could not find OsLibFunc constructor, did the Lua library change?");
    }
    private static VarArgFunction reflectiveOsLibFunc(OsLib lib, int opcode, String name) {
        try {
            return OsLibFuncConstructor.newInstance(lib, opcode, name);
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
