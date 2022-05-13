package com.dosmike.spsauce.tasks.luavm;

import com.dosmike.spsauce.script.BuildScript;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

public class JLuaSPSauceLib extends TwoArgFunction {
    public JLuaSPSauceLib() {
    }

    static class _getenv extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue arg) {
            try {
                return valueOf(BuildScript.parseVariable("${" + arg.checkjstring() + "}"));
            } catch (IllegalArgumentException e) {
                return error("Env name has to be alphanumeric");
            } catch (RuntimeException e) {
                return NIL;
            }
        }
    }

    static class _setenv extends TwoArgFunction {
        @Override
        public LuaValue call(LuaValue arg1, LuaValue arg2) {
            try {
                BuildScript.defineRef("${" + arg1.checkjstring() + "}", arg2.checkjstring());
                return NONE;
            } catch (IllegalArgumentException ignore) {
                return error("Env name has to be alphanumeric");
            }
        }
    }

    static class _getvar extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue arg) {
            try {
                return valueOf(BuildScript.parseVariable("%{" + arg.checkjstring() + "}"));
            } catch (IllegalArgumentException e) {
                return error("Var name has to be alphanumeric");
            } catch (RuntimeException e) {
                return NIL;
            }
        }
    }

    static class _setvar extends TwoArgFunction {
        @Override
        public LuaValue call(LuaValue arg1, LuaValue arg2) {
            try {
                BuildScript.defineRef("%{" + arg1.checkjstring() + "}", arg2.checkjstring());
                return NONE;
            } catch (IllegalArgumentException ignore) {
                return error("Var name has to be alphanumeric");
            }
        }
    }

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable sps = new LuaTable(0, 30);
        sps.set("getenv", new _getenv());
        sps.set("setenv", new _setenv());
        sps.set("getvar", new _getvar());
        sps.set("setvar", new _setvar());
        env.set("sps", sps);
        env.get("package").get("loaded").set("sps", sps);
        return sps;
    }
}
