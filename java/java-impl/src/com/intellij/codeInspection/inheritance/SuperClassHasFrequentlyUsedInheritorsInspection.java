package com.intellij.codeInspection.inheritance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.inheritance.search.InheritorsStatisticalDataSearch;
import com.intellij.codeInspection.inheritance.search.InheritorsStatisticsSearchResult;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class SuperClassHasFrequentlyUsedInheritorsInspection extends BaseJavaLocalInspectionTool {
  private final static int MIN_PERCENT_RATIO = 5;
  public final static int MAX_QUICK_FIX_COUNTS = 4;

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.INHERITANCE_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Class may extend a commonly used base class instead of implementing interface or extending abstract class";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkClass(@NotNull final PsiClass aClass,
                                        @NotNull final InspectionManager manager,
                                        final boolean isOnTheFly) {
    if (aClass.isInterface() ||
        aClass instanceof PsiTypeParameter ||
        aClass.getMethods().length != 0 ||
        aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return null;
    }

    final PsiClass superClass = getSuperIfUnique(aClass);
    if (superClass == null) return null;

    final List<InheritorsStatisticsSearchResult> topInheritors =
      InheritorsStatisticalDataSearch.search(superClass, aClass, aClass.getResolveScope(), MIN_PERCENT_RATIO);

    if (topInheritors.isEmpty()) {
      return null;
    }

    final Collection<LocalQuickFix> topInheritorsQuickFix = new ArrayList<LocalQuickFix>(topInheritors.size());

    boolean isFirst = true;
    for (final InheritorsStatisticsSearchResult searchResult : topInheritors) {
      LocalQuickFix quickFix = new ChangeSuperClassFix(searchResult.getPsiClass(), searchResult.getPercent(), superClass);
      if (isFirst) {
        quickFix = ChangeSuperClassFix.highPriority(quickFix);
        isFirst = false;
      }
      topInheritorsQuickFix.add(quickFix);
      if (topInheritorsQuickFix.size() >= MAX_QUICK_FIX_COUNTS) {
        break;
      }
    }
    return new ProblemDescriptor[]{manager
      .createProblemDescriptor(aClass, "Class may extend a commonly used base class instead of implementing interface or extending abstract class", false,
                               topInheritorsQuickFix.toArray(new LocalQuickFix[topInheritorsQuickFix.size()]),
                               ProblemHighlightType.INFORMATION)};
  }

  @Nullable
  private static PsiClass getSuperIfUnique(final @NotNull PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return (PsiClass)((PsiAnonymousClass)aClass).getBaseClassReference().resolve();
    }
    final PsiReferenceList extendsList = aClass.getExtendsList();
    if (extendsList != null) {
      final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
      if (referenceElements.length == 1) {
        PsiClass returnClass = (PsiClass)referenceElements[0].resolve();
        if (returnClass != null &&
            !CommonClassNames.JAVA_LANG_OBJECT.equals(returnClass.getQualifiedName()) &&
            !returnClass.isInterface()) {
          return returnClass;
        }
      }
    }

    final PsiReferenceList implementsList = aClass.getImplementsList();
    if (implementsList != null) {
      final PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
      if (referenceElements.length == 1) {
        PsiClass returnClass = (PsiClass)referenceElements[0].resolve();
        if (returnClass != null && returnClass.isInterface()) {
          return returnClass;
        }
      }
    }
    return null;
  }
}
