package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightModifierList;
import de.plushnikov.intellij.plugin.psi.LombokLightParameter;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.thirdparty.LombokCopyableAnnotations;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Setter lombok annotation on a field
 * Creates setter method for this field
 *
 * @author Plushnikov Michail
 */
public final class SetterFieldProcessor extends AbstractFieldProcessor {
  SetterFieldProcessor() {
    super(PsiMethod.class, LombokClassNames.SETTER);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiField psiField,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    final PsiClass psiClass = psiField.getContainingClass();
    if (methodVisibility != null && psiClass != null) {
      target.add(createSetterMethod(psiField, psiClass, methodVisibility));
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result;
    validateOnXAnnotations(psiAnnotation, psiField, builder, "onParam");

    result = validateFinalModifier(psiAnnotation, psiField, builder);
    if (result) {
      result = validateVisibility(psiAnnotation);
      if (result) {
        result = validateExistingMethods(psiField, builder, false);
        if (result) {
          result = validateAccessorPrefix(psiField, builder);
        }
      }
    }
    return result;
  }

  private boolean validateFinalModifier(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiField.hasModifierProperty(PsiModifier.FINAL) && null != LombokProcessorUtil.getMethodModifier(psiAnnotation)) {
      builder.addWarning(LombokBundle.message("inspection.message.not.generating.setter.for.this.field.setters"),
                         PsiQuickFixFactory.createModifierListFix(psiField, PsiModifier.FINAL, false, false));
      result = false;
    }
    return result;
  }

  private boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibility;
  }

  private boolean validateAccessorPrefix(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (AccessorsInfo.build(psiField).isPrefixUnDefinedOrNotStartsWith(psiField.getName())) {
      builder.addWarning(LombokBundle.message("inspection.message.not.generating.setter.for.this.field.it"));
      result = false;
    }
    return result;
  }

  public Collection<String> getAllSetterNames(@NotNull PsiField psiField, boolean isBoolean) {
    final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
    return LombokUtils.toAllSetterNames(accessorsInfo, psiField.getName(), isBoolean);
  }

  @NotNull
  public PsiMethod createSetterMethod(@NotNull PsiField psiField, @NotNull PsiClass psiClass, @NotNull String methodModifier) {
    final String fieldName = psiField.getName();
    final PsiType psiFieldType = psiField.getType();
    final PsiAnnotation setterAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, LombokClassNames.SETTER);

    final String methodName = LombokUtils.getSetterName(psiField);

    PsiType returnType = getReturnType(psiField);
    LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiField.getManager(), methodName)
      .withMethodReturnType(returnType)
      .withContainingClass(psiClass)
      .withParameter(fieldName, psiFieldType)
      .withNavigationElement(psiField);
    if (StringUtil.isNotEmpty(methodModifier)) {
      methodBuilder.withModifier(methodModifier);
    }
    boolean isStatic = psiField.hasModifierProperty(PsiModifier.STATIC);
    if (isStatic) {
      methodBuilder.withModifier(PsiModifier.STATIC);
    }

    LombokLightParameter setterParameter = methodBuilder.getParameterList().getParameter(0);
    if(null!=setterParameter) {
      LombokLightModifierList methodParameterModifierList = setterParameter.getModifierList();
      copyCopyableAnnotations(psiField, methodParameterModifierList, LombokCopyableAnnotations.BASE_COPYABLE);
      copyOnXAnnotations(setterAnnotation, methodParameterModifierList, "onParam");
    }

    final LombokLightModifierList modifierList = methodBuilder.getModifierList();
    copyCopyableAnnotations(psiField, modifierList, LombokCopyableAnnotations.COPY_TO_SETTER);
    copyOnXAnnotations(setterAnnotation, modifierList, "onMethod");
    if (psiField.isDeprecated()) {
      modifierList.addAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED);
    }

    final String codeBlockText = createCodeBlockText(psiField, psiClass, returnType, isStatic, setterParameter);
    methodBuilder.withBodyText(codeBlockText);

    return methodBuilder;
  }

  @NotNull
  private String createCodeBlockText(@NotNull PsiField psiField,
                                     @NotNull PsiClass psiClass,
                                     PsiType returnType,
                                     boolean isStatic,
                                     PsiParameter methodParameter) {
    final String blockText;
    final String thisOrClass = isStatic ? psiClass.getName() : "this";
    blockText = String.format("%s.%s = %s; ", thisOrClass, psiField.getName(), methodParameter.getName());

    String codeBlockText = blockText;
    if (!isStatic && !PsiType.VOID.equals(returnType)) {
      codeBlockText += "return this;";
    }

    return codeBlockText;
  }

  private PsiType getReturnType(@NotNull PsiField psiField) {
    PsiType result = PsiType.VOID;
    if (!psiField.hasModifierProperty(PsiModifier.STATIC) && AccessorsInfo.build(psiField).isChain()) {
      final PsiClass fieldClass = psiField.getContainingClass();
      if (null != fieldClass) {
        result = PsiClassUtil.getTypeWithGenerics(fieldClass);
      }
    }
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.WRITE;
  }
}
