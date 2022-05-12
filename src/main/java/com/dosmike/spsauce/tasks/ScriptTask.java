package com.dosmike.spsauce.tasks;

import com.dosmike.spsauce.Task;
import com.dosmike.spsauce.tasks.luavm.JLuaOSLib;
import com.dosmike.spsauce.tasks.luavm.JLuaSPSauceLib;
import com.dosmike.spsauce.tasks.luavm.ReadOnlyLuaTable;
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
            // this is a modified version of the os lib that does not allow file access
            uglobals.load(new JLuaOSLib());
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
