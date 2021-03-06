/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.CommonProcessors;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class JavaFxIdsIndex extends FileBasedIndexExtension<String, Set<String>> {

  @NonNls public static final ID<String, Set<String>> KEY = ID.create("javafx.id.name");

  private final KeyDescriptor<String> myKeyDescriptor = new EnumeratorStringDescriptor();
  private final FileBasedIndex.InputFilter myInputFilter = new JavaFxControllerClassIndex.MyInputFilter();
  private final FxmlDataIndexer myDataIndexer = new FxmlDataIndexer();
  private final FxmlDataExternalizer myDataExternalizer = new FxmlDataExternalizer();

  @NotNull
  @Override
  public DataIndexer<String, Set<String>, FileContent> getIndexer() {
    return myDataIndexer;
  }

  @Override
  public DataExternalizer<Set<String>> getValueExternalizer() {
    return myDataExternalizer;
  }

  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  @NotNull
  @Override
  public ID<String, Set<String>> getName() {
    return KEY;
  }

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @NotNull
  public static Collection<String> getAllRegisteredIds(Project project, Set<String> fxmls) {
    CommonProcessors.CollectUniquesProcessor<String> processor = new CommonProcessors.CollectUniquesProcessor<String>();
    FileBasedIndex.getInstance().processAllKeys(KEY, processor, project);
    final Collection<String> results = new ArrayList<String>(processor.getResults());
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
    for (Iterator<String> iterator = results.iterator(); iterator.hasNext(); ) {
      final String id = iterator.next();
      final List<Set<String>> values = FileBasedIndex.getInstance().getValues(KEY, id, searchScope);
      if (!values.isEmpty()) {
        final Set<String> pathSet = values.get(0);
        if (pathSet != null && !Collections.disjoint(pathSet, fxmls)) {
          continue;
        }
      }
      iterator.remove();
    }
    return results;
  }

  @NotNull
  public static Collection<String> getFilePaths(Project project, String id) {
    final List<Set<String>> values = FileBasedIndex.getInstance().getValues(KEY, id, GlobalSearchScope.projectScope(project));
    return (Collection<String>)(values.isEmpty() ? Collections.emptySet() : values.get(0));
  }
}