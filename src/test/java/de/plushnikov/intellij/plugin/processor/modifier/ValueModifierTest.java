package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexej Kubarev
 */
public class ValueModifierTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return "testData/augment/modifier";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_DESCRIPTOR;
  }

  public void testValueModifiers() {

    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");

    PsiField field = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiField.class);

    assertNotNull(field);
    assertNotNull(field.getModifierList());

    assertTrue("@Value should make variable final", field.getModifierList().hasModifierProperty(PsiModifier.FINAL));
    assertTrue("@Value should make variable private", field.getModifierList().hasModifierProperty(PsiModifier.PRIVATE));

    PsiClass clazz = PsiTreeUtil.getParentOfType(field, PsiClass.class);

    assertNotNull(clazz);

    PsiModifierList list = clazz.getModifierList();

    assertNotNull(list);
    assertTrue("@Value should make class final", list.hasModifierProperty(PsiModifier.FINAL));
    assertFalse("@Value should not make class private", list.hasModifierProperty(PsiModifier.PRIVATE));
    assertFalse("@Value should not make class static", list.hasModifierProperty(PsiModifier.STATIC));
  }

  public void testValueModifiersStatic() {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");

    PsiField field = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiField.class);
    assertNotNull(field);
    assertNotNull(field.getModifierList());

    assertFalse("@Value should not make static variable final", field.getModifierList().hasModifierProperty(PsiModifier.FINAL));
    assertFalse("@Value should not make static variable private", field.getModifierList().hasModifierProperty(PsiModifier.PRIVATE));
  }
}
