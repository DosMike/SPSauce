package com.dosmike.spsauce;

import java.io.File;
import java.io.IOException;

public interface PluginSource {

    /**
     * @param criteria source dependant criteria
     * @return true if this source contains the dependency
     */
    Plugin search(String... criteria) throws IOException;

    /**
     * @return true on success
     */
    boolean fetch(Plugin dep) throws IOException;

    /**
     * Push a plugin release to a platform
     */
    void push(Plugin meta, File... resources);

}
