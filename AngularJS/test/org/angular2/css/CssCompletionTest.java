// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.angular2.css;

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.angularjs.AngularTestUtil;

import static com.intellij.util.containers.ContainerUtil.sorted;
import static java.util.Arrays.asList;

public class CssCompletionTest extends LightPlatformCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return AngularTestUtil.getBaseTestDataPath(getClass()) + "completion";
  }

  public void testLocalCssCompletionHtml() {
    myFixture.configureByFiles("local-stylesheet-ext.html", "local-stylesheet-ext.ts", "local-stylesheet-ext.css", "package.json");
    AngularTestUtil.moveToOffsetBySignature("class=\"<caret>\"", myFixture);
    myFixture.completeBasic();
    assertEquals(asList("", "local-class-ext", "local-class-int"), sorted(myFixture.getLookupElementStrings()));
    myFixture.type('\n');
    AngularTestUtil.moveToOffsetBySignature("id=\"<caret>\"", myFixture);
    myFixture.completeBasic();
    assertEquals(asList("local-id-ext", "local-id-int"), sorted(myFixture.getLookupElementStrings()));
  }

  public void testLocalCssCompletionLocalCss() {
    myFixture.configureByFiles("local-stylesheet-ext.ts", "local-stylesheet-ext.css", "local-stylesheet-ext.html", "package.json");
    AngularTestUtil.moveToOffsetBySignature(".<caret> {", myFixture);
    myFixture.completeBasic();
    assertEquals(asList("class-in-html", "local-class-ext"), sorted(myFixture.getLookupElementStrings()));
    myFixture.type('\n');
    AngularTestUtil.moveToOffsetBySignature("#<caret> {", myFixture);
    myFixture.completeBasic();
    assertEquals(asList("id-in-html", "local-id-ext"), sorted(myFixture.getLookupElementStrings()));
  }
}
