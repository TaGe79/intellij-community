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
package com.intellij.codeInspection.inheritance;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class ChangeSuperClassFix implements LocalQuickFix {
  @NotNull
  private final PsiClass myNewSuperClass;
  @NotNull
  private final PsiClass myOldSuperClass;
  private final int myPercent;

  public ChangeSuperClassFix(final @NotNull PsiClass newSuperClass, final int percent, final @NotNull PsiClass oldSuperClass) {
    this.myNewSuperClass = newSuperClass;
    this.myOldSuperClass = oldSuperClass;
    this.myPercent = percent;
  }

  @NotNull
  @TestOnly
  public PsiClass getNewSuperClass() {
    return myNewSuperClass;
  }

  @TestOnly
  public int getPercent() {
    return myPercent;
  }

  @NotNull
  @Override
  public String getName() {
    return String.format("%s%% extends %s", myPercent, myNewSuperClass.getQualifiedName());
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroupNames.INHERITANCE_GROUP_NAME;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor problemDescriptor) {
    changeSuperClass((PsiClass)problemDescriptor.getPsiElement(), myOldSuperClass, myNewSuperClass);
  }

  /**
   * myOldSuperClass and myNewSuperClass can be interfaces or classes in any combination
   * <p/>
   * 1. not checks that myOldSuperClass is really super of aClass
   * 2. not checks that myNewSuperClass not exists in currently existed supers
   */
  private static void changeSuperClass(final @NotNull PsiClass aClass,
                                       final @NotNull PsiClass oldSuperClass,
                                       final @NotNull PsiClass newSuperClass) {
    if (!CodeInsightUtilBase.preparePsiElementForWrite(aClass)) return;

    new WriteCommandAction.Simple(newSuperClass.getProject(), aClass.getContainingFile()) {
      @Override
      protected void run() throws Throwable {
        PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
        if (aClass instanceof PsiAnonymousClass) {
          ((PsiAnonymousClass)aClass).getBaseClassReference().replace(factory.createClassReferenceElement(newSuperClass));
        }
        else if (oldSuperClass.isInterface()) {
          final PsiReferenceList interfaceList = aClass.getImplementsList();
          if (interfaceList != null) {
            for (final PsiJavaCodeReferenceElement interfaceRef : interfaceList.getReferenceElements()) {
              final PsiElement aInterface = interfaceRef.resolve();
              if (aInterface != null && aInterface.isEquivalentTo(oldSuperClass)) {
                interfaceRef.delete();
              }
            }
          }

          final PsiReferenceList extendsList = aClass.getExtendsList();
          if (extendsList != null) {
            final PsiJavaCodeReferenceElement newClassReference = factory.createClassReferenceElement(newSuperClass);
            if (extendsList.getReferenceElements().length == 0) {
              extendsList.add(newClassReference);
            }
          }
        }
        else {
          final PsiReferenceList extendsList = aClass.getExtendsList();
          if (extendsList != null && extendsList.getReferenceElements().length == 1) {
            extendsList.getReferenceElements()[0].delete();
            PsiElement ref = extendsList.add(factory.createClassReferenceElement(newSuperClass));
            JavaCodeStyleManager.getInstance(aClass.getProject()).shortenClassReferences(ref);
          }
        }
      }
    }.execute();
  }

  public static LocalQuickFix highPriority(final LocalQuickFix quickFix) {
    return new HighPriorityQuickFixWrapper(quickFix);
  }

  public static class HighPriorityQuickFixWrapper implements LocalQuickFix, HighPriorityAction {

    private final LocalQuickFix myUnderlying;

    private HighPriorityQuickFixWrapper(final LocalQuickFix underlying) {
      myUnderlying = underlying;
    }

    @TestOnly
    public LocalQuickFix getUnderlying() {
      return myUnderlying;
    }

    @NotNull
    @Override
    public String getName() {
      return myUnderlying.getName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return myUnderlying.getFamilyName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      myUnderlying.applyFix(project, descriptor);
    }
  }
}
