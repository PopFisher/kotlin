/*
 * Copyright 2010-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.plugin.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Pavel Talanov
 *         <p/>
 *         This class has utility functions to determine whether the project (or module) is js project.
 */
public final class JsModuleDetector {

    public static final String INDICATION_FILE_NAME = ".kotlin-js";

    private JsModuleDetector() {
    }

    public static boolean isJsProject(@NotNull Project project) {
        return findIndicationFileInContentRoots(project) != null;
    }

    @Nullable
    public static VirtualFile findIndicationFileInContentRoots(@NotNull Project project) {
        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        for (VirtualFile root : roots) {
            for (VirtualFile child : root.getChildren()) {
                if (child.getName().equals(INDICATION_FILE_NAME)) {
                    return child;
                }
            }
        }
        return null;
    }

    //TODO: refactor
    @NotNull
    public static Pair<String, String> getLibLocationAndTargetForProject(@NotNull Project project) {
        VirtualFile indicationFile = findIndicationFileInContentRoots(project);
        if (indicationFile == null) {
            return Pair.empty();
        }

        try {
            InputStream stream = indicationFile.getInputStream();
            BufferedReader reader = new BufferedReader(new StringReader(FileUtil.loadTextAndClose(stream)));
            try {
                String pathToLibFile = reader.readLine();
                if (pathToLibFile == null) {
                    return Pair.empty();
                }

                URI pathToLibFileUri = new URI(pathToLibFile);
                URI pathToIndicationFileUri = new URI(indicationFile.getPath());
                return new Pair<String, String>(pathToIndicationFileUri.resolve(pathToLibFileUri).toString(), reader.readLine());
            }
            catch (URISyntaxException e) {
                return Pair.empty();
            }
            finally {
                reader.close();
            }
        }
        catch (IOException e) {
            return Pair.empty();
        }
    }
}
