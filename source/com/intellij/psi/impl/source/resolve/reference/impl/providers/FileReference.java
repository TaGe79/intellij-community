package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.ElementManipulator;
import com.intellij.psi.impl.source.resolve.reference.ProcessorRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class FileReference extends GenericReference implements PsiPolyVariantReference, QuickFixProvider<FileReference> {
  public static final FileReference[] EMPTY = new FileReference[0];
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference");

  private final int myIndex;
  private TextRange myRange;
  private final String myText;
  @NotNull private final FileReferenceSet myFileReferenceSet;
  private final Condition<String> myEqualsToCondition = new Condition<String>() {
    public boolean value(String s) {
      return equalsTo(s);
    }
  };

  public FileReference(final @NotNull FileReferenceSet fileReferenceSet, TextRange range, int index, String text){
    super(fileReferenceSet.getProvider());
    myFileReferenceSet = fileReferenceSet;
    myIndex = index;
    myRange = range;
    myText = text;
  }

  @NotNull private Collection<PsiElement> getContexts(){
    final FileReference contextRef = getContextReference();
    if (contextRef == null) {
      return myFileReferenceSet.getDefaultContexts(myFileReferenceSet.getElement());
    }
    else {
      ResolveResult[] resolveResults = contextRef.multiResolve(false);
      ArrayList<PsiElement> result = new ArrayList<PsiElement>();
      for (ResolveResult resolveResult : resolveResults) {
        result.add(resolveResult.getElement());
      }
      return result;
    }
  }

  @Nullable
  public static PsiDirectory getPsiDirectory(PsiElement element) {
    for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      final PsiDirectory directory = helper.getPsiDirectory(element);
      if (directory != null) {
        return directory;
      }
    }
    return null;
  }

  @Nullable
  public PsiElement getContext() {
    final PsiReference contextRef = getContextReference();
    return contextRef != null ? contextRef.resolve() : null;
  }

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final PsiManager manager = getElement().getManager();
    if (manager instanceof PsiManagerImpl) {
      return ((PsiManagerImpl) manager).getResolveCache().resolveWithCaching(
        this, MyResolver.INSTANCE, false, false
      );
    }
    return innerResolve();
  }

  @NotNull
  private FileReferenceContext getFileReferenceContext(PsiElement context) {
    for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      final FileReferenceContext referenceContext = helper.getFileReferenceContext(context);
      if (referenceContext != null) {
        return referenceContext;
      }
    }
    return new FileReferenceContext() {
      @Nullable
      public PsiElement innerResolve(String text, final Condition<String> equalsTo) {
        return null;
      }

      public boolean processVariants(PsiScopeProcessor processor) {
        if(getContextReference() == null){
        myFileReferenceSet.getProvider().handleEmptyContext(processor, getElement());
      }
        return true;
      }
    };
  }

  protected ResolveResult[] innerResolve() {
    final String text = getText();
    final Collection<PsiElement> contexts = getContexts();
    final Collection<ResolveResult> result = new ArrayList<ResolveResult>(contexts.size());

    for (final PsiElement context : contexts) {
      PsiElement resolved = getFileReferenceContext(context).innerResolve(text, myEqualsToCondition);
      if (resolved != null) {
        result.add(new PsiElementResolveResult(resolved));
      }
    }
    final int resultCount = result.size();
    return resultCount > 0 ? result.toArray(new ResolveResult[resultCount]) : ResolveResult.EMPTY_ARRAY;
  }

  public Object[] getVariants(){
    final String s = getText();
    if (s != null && s.equals("/")) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    try{
      final List ret = new ArrayList();
      final PsiScopeProcessor proc = myFileReferenceSet.createProcessor(ret, getSoftenType());
      processVariants(proc);
      return ret.toArray();
    }
    catch(ProcessorRegistry.IncompatibleReferenceTypeException e){
      LOG.error(e);
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }

  public void processVariants(@NotNull final PsiScopeProcessor processor) {
    for (PsiElement context : getContexts()) {
      if (!getFileReferenceContext(context).processVariants(processor)) return;
    }
  }

  @Nullable
  public FileReference getContextReference(){
    return myIndex > 0 ? myFileReferenceSet.getReference(myIndex - 1) : null;
  }

  @NotNull
  public ReferenceType getType(){
    return myFileReferenceSet.getType(myIndex);
  }

  public ReferenceType getSoftenType(){
    return new ReferenceType(new int[] {ReferenceType.WEB_DIRECTORY_ELEMENT, ReferenceType.FILE, ReferenceType.DIRECTORY});
  }

  public PsiElement getElement(){
    return myFileReferenceSet.getElement();
  }

  public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  private boolean equalsTo(final String name) {
    return myFileReferenceSet.isCaseSensitive() ? getText().equals(name) :
           getText().compareToIgnoreCase(name) == 0;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (!isTargetAccepted(element)) return false;

    final PsiElement resolveResult = resolve();
    for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      if (helper.isReferenceTo(element, resolveResult)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isTargetAccepted(final PsiElement element) {
    for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      if (helper.isTargetAccepted(element)) {
        return true;
      }
    }
    return false;
  }

  public TextRange getRangeInElement(){
    return myRange;
  }

  public String getCanonicalText(){
    return myText;
  }

  protected String getText() {
    return myText;
  }

  public boolean isSoft(){
    return myFileReferenceSet.isSoft();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    if (manipulator != null) {
      myFileReferenceSet.setElement(manipulator.handleContentChange(getElement(), getRangeInElement(), newElementName));
      //Correct ranges
      int delta = newElementName.length() - myRange.getLength();
      myRange = new TextRange(getRangeInElement().getStartOffset(), getRangeInElement().getStartOffset() + newElementName.length());
      FileReference[] references = myFileReferenceSet.getAllReferences();
      for (int idx = myIndex + 1; idx < references.length; idx++) {
        references[idx].myRange = references[idx].myRange.shiftRight(delta);
      }
      return myFileReferenceSet.getElement();
    }
    throw new IncorrectOperationException("Manipulator for this element is not defined");
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException{
    if (!(element instanceof PsiFileSystemItem)) throw new IncorrectOperationException("Cannot bind to element");
    PsiFileSystemItem fileSystemItem = (PsiFileSystemItem) element;
    VirtualFile dstVFile = PsiUtil.getVirtualFile(fileSystemItem);

    final PsiFile file = getElement().getContainingFile();
    if (dstVFile == null) throw new IncorrectOperationException("Cannot bind to non-physical element:" + element);

    for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      if (helper.doNothingOnBind(file, this)) {
        return element;
      }
    }

    final VirtualFile currentFile = file.getVirtualFile();
    LOG.assertTrue(currentFile != null);

    String newName = null;
    for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      final String s = helper.getRelativePath(file.getProject(), currentFile, dstVFile);
      if (s != null) {
        newName = s;
        break;
      }
    }

    if (newName == null) {
      throw new IncorrectOperationException("Cannot find path between files; src = " +
                                            currentFile.getPresentableUrl() + "; dst = " + dstVFile.getPresentableUrl());
    }

    final TextRange range = new TextRange(myFileReferenceSet.getStartInElement(), getRangeInElement().getEndOffset());
    final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    if (manipulator == null) {
      throw new IncorrectOperationException("Manipulator not defined for: " + getElement());
    }
    return manipulator.handleContentChange(getElement(), range, newName);
  }

  public void registerQuickfix(HighlightInfo info, FileReference reference) {
    for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      helper.registerQuickfix(info, reference);
    }
  }

  public int getIndex() {
    return myIndex;
  }

  @NotNull
  public FileReferenceSet getFileReferenceSet() {
    return myFileReferenceSet;
  }

  public boolean needToCheckAccessibility() {
    return false;
  }

  public void clearResolveCaches() {
    final PsiManager manager = getElement().getManager();
    if (manager instanceof PsiManagerImpl) {
      ((PsiManagerImpl)manager).getResolveCache().clearResolveCaches(this);
    }
  }

  static class MyResolver implements ResolveCache.PolyVariantResolver {
    static MyResolver INSTANCE = new MyResolver();
    public ResolveResult[] resolve(PsiPolyVariantReference ref, boolean incompleteCode) {
      return ((FileReference)ref).innerResolve();
    }
  }
}
