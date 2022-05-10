package com.dosmike.spsauce.tasks;

import com.dosmike.spsauce.Task;
import com.dosmike.spsauce.release.FileSet;

public abstract class ReleaseTask implements Task {

    protected final FileSet files;
    protected ReleaseTask(FileSet files) {
        this.files = files;
    }

}
