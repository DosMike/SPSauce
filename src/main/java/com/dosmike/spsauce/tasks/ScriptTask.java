package com.dosmike.spsauce.tasks;

import com.dosmike.spsauce.Task;
import com.dosmike.spsauce.script.BuildScript;
import org.luaj.vm2.*;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.*;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;

public abstract class ScriptTask implements Task {

    //Adapted from the LuaJ SandBox example located at
    // https://github.com/luaj/luaj/blob/master/examples/jse/SampleSandboxed.java
    public static class Lua extends ScriptTask {

        // These globals are used by the server to compile scripts.
        static Globals sglobals;
        static {
            SetupStaticGlobal();
        }
        private static void SetupStaticGlobal() {
            sglobals = new Globals();
            sglobals.load(new JseBaseLib());
            sglobals.load(new PackageLib());
            sglobals.load(new StringLib());
            sglobals.load(new JseMathLib());
            LoadState.install(sglobals);
            LuaC.install(sglobals);
            LuaString.s_metatable = new ReadOnlyLuaTable(LuaString.s_metatable);
        }
        static class ReadOnlyLuaTable extends LuaTable {
            public ReadOnlyLuaTable(LuaValue table) {
                presize(table.length(), 0);
                for (Varargs n = table.next(LuaValue.NIL); !n.arg1().isnil(); n = table
                        .next(n.arg1())) {
                    LuaValue key = n.arg1();
                    LuaValue value = n.arg(2);
                    super.rawset(key, value.istable() ? new ReadOnlyLuaTable(value) : value);
                }
            }
            public LuaValue setmetatable(LuaValue metatable) { return error("table is read-only"); }
            public void set(int key, LuaValue value) { error("table is read-only"); }
            public void rawset(int key, LuaValue value) { error("table is read-only"); }
            public void rawset(LuaValue key, LuaValue value) { error("table is read-only"); }
            public LuaValue remove(int pos) { return error("table is read-only"); }
        }

        static class JLuaSPSauceLib extends TwoArgFunction {
            public JLuaSPSauceLib() {}
            static class _getenv extends OneArgFunction {
                @Override
                public LuaValue call(LuaValue arg) {
                    try {
                        return valueOf( BuildScript.parseVariable("${" + arg.checkjstring() + "}") );
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
                        return valueOf( BuildScript.parseVariable("%{" + arg.checkjstring() + "}") );
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
                LuaTable sps = new LuaTable(0,30);
                sps.set("getenv", new _getenv());
                sps.set("setenv", new _setenv());
                sps.set("getvar", new _getvar());
                sps.set("setvar", new _setvar());
                env.set("sps", sps);
                env.get("package").get("loaded").set("sps", sps);
                return sps;
            }
        }

        String code;

        public Lua(String script) {
            this.code = script;
        }

        @Override
        public void run() throws Throwable {
            Globals uglobals = new Globals();
            uglobals.load(new JseBaseLib());
            uglobals.load(new PackageLib());
            uglobals.load(new Bit32Lib());
            uglobals.load(new TableLib());
            uglobals.load(new StringLib());
            uglobals.load(new JseMathLib());
            // inject our own library for accessing script context vars
            uglobals.load(new JLuaSPSauceLib());
            //prepare lua thread
            LuaValue chunk = sglobals.load(this.code, "main", uglobals);
            LuaThread thread = new LuaThread(uglobals, chunk);
            //use debug library for timeout, but hide from user
            uglobals.load(new DebugLib());
            LuaValue sethook = uglobals.get("debug").get("sethook");
            uglobals.set("debug", LuaValue.NIL);
            //hook a far off instruction count to prevent endless execution time
            LuaValue hookfunc = new ZeroArgFunction() {
                public LuaValue call() {
                    throw new Error("Script overran resource limits.");
                }
            };
            sethook.invoke(LuaValue.varargsOf(new LuaValue[]{thread, hookfunc, LuaValue.EMPTYSTRING, LuaValue.valueOf(1000000)}));
            //run thread
            Varargs result = thread.resume(LuaValue.NIL); //returns [success,valueOrError]
            if (!result.checkboolean(1)) throw new LuaError(result.checkjstring(2));
        }
    }

}
