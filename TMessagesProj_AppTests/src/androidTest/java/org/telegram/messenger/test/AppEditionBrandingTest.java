package org.telegram.messenger.test;

import org.junit.Test;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AppEditionBrandingTest {
    @Test
    public void applicationIdSelectsConsistentEditionBrand() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean extera = context.getPackageName().startsWith("com.exteragram.messenger");
        String expectedEdition = extera ? "extera" : "femboy";
        String expectedBrand = extera ? "ExteraGram" : "FemboyGram";

        assertEquals(expectedEdition, BuildVars.APP_EDITION);
        assertEquals(extera, BuildVars.isExteraGram());
        assertTrue(LocaleController.getString(R.string.AppName).contains(expectedBrand));
        assertEquals("https://t.me/FemboyGramTips", LocaleController.getString(R.string.TelegramFeaturesUrl));
    }
}
