/*
 * The MIT License
 *
 * Copyright (c) 2010 Tomasz Blaszczynski, Emanuele Zattin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jvnet.hudson.plugins.periodicbackup;

import com.google.common.collect.Lists;
import hudson.model.Hudson;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class ReplaceRestorePolicy implements RestorePolicy {

    private static final Logger LOGGER = Logger.getLogger(ReplaceRestorePolicy.class.getName());
    private File hudsonRoot;
    private List<String> autoExclusionList;
    private transient int filesDeleted, filesReplaced, filesKept;

    public void restore(File tempDir) throws IOException, PeriodicBackupException {
        hudsonRoot = Hudson.getInstance().getRootDir();
        if(hudsonRoot == null) {
            throw new PeriodicBackupException("HOME directory is unidentified.");
        }
        autoExclusionList = Lists.newArrayList();
        filesDeleted = 0;
        filesReplaced = 0;
        filesKept = 0;

        deleteAccessible(hudsonRoot.listFiles());
        LOGGER.info(filesDeleted + " files have been deleted from " + hudsonRoot.getAbsolutePath());
        replaceAccessible(tempDir.listFiles(), tempDir);
        LOGGER.info("Replacing of files finished.\nAfter deleting " + filesDeleted + " files from " +
                hudsonRoot.getAbsolutePath() + "\n" + filesReplaced + " files have been restored from backup and "
                + filesKept + " files have been kept.");
    }

    public void deleteAccessible(File[] files) throws PeriodicBackupException {
        String relativePath;
        for(File file : files) {
            if(!file.isDirectory()) {
                if(!file.canWrite()) {
                    LOGGER.warning("Access denied to " + file.getAbsolutePath() + ", file will not be replaced");
                    relativePath = Util.getRelativePath(file, hudsonRoot);
                    autoExclusionList.add(relativePath);
                }
                else {
                    if(!file.delete()) {
                        LOGGER.warning("Access denied to " + file.getAbsolutePath() + ", file will not be replaced");
                        relativePath = Util.getRelativePath(file, hudsonRoot);
                        autoExclusionList.add(relativePath);
                    }
                    else {
                        filesDeleted++;
                    }
                }
            }
            else {
                deleteAccessible(file.listFiles());
            }
        }
    }

    public void replaceAccessible(File[] files, File tempDir) throws IOException {
        String relativePath;
        File destinationFile;
        for(File file : files) {
            //empty directories will not be created
            if(!file.isDirectory()) {
                relativePath = Util.getRelativePath(file, tempDir);
                if(     autoExclusionList.size() == 0 ||
                        autoExclusionList == null ||
                        (autoExclusionList.size() > 0 && !autoExclusionList.contains(relativePath))) {
                    LOGGER.info("Copying " + file.getAbsolutePath() + " to " + hudsonRoot.getAbsolutePath());
                    destinationFile = new File(hudsonRoot, relativePath);
                    FileUtils.copyFile(file, destinationFile);
                    filesReplaced++;
                }
                else if(autoExclusionList != null && autoExclusionList.contains(relativePath)) {
                        LOGGER.warning("File " + file.getAbsolutePath() + " is excluded from the restore process, original file will be kept");
                        filesKept++;
                }
            }
            else {
                replaceAccessible(file.listFiles(), tempDir);
            }
        }
    }

   @Override
    public boolean equals(Object o) {
        return o instanceof ReplaceRestorePolicy;
    }

    @Override
    public int hashCode() {
        return 83;
    }
}
