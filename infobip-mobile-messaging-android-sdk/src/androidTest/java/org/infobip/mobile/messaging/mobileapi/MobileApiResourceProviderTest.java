package org.infobip.mobile.messaging.mobileapi;

import org.infobip.mobile.messaging.MobileMessagingCore;
import org.infobip.mobile.messaging.MobileMessagingProperty;
import org.infobip.mobile.messaging.api.support.ApiIOException;
import org.infobip.mobile.messaging.api.support.CustomApiHeaders;
import org.infobip.mobile.messaging.api.version.MobileApiVersion;
import org.infobip.mobile.messaging.tools.MobileMessagingTestCase;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * @author sslavin
 * @since 27/11/2017.
 */

public class MobileApiResourceProviderTest extends MobileMessagingTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mobileApiResourceProvider = new MobileApiResourceProvider();
    }

    @Test
    public void shouldSaveBaseUrlFromResponse() {
        // given
        debugServer.respondWith(NanoHTTPD.Response.Status.OK, null, new HashMap<String, String>() {{
            put(CustomApiHeaders.NEW_BASE_URL.getValue(), "customUrl");
        }});

        // when
        mobileApiResourceProvider.getMobileApiVersion(context).getLatestRelease();

        // then
        assertEquals("customUrl", MobileMessagingCore.getApiUri(context, false));
    }

    @Test
    public void shouldCalculateAppCodeHashInRequest() {
        // when
        try {
            mobileApiResourceProvider.getMobileApiVersion(context).getLatestRelease();
            fail();
        } catch (ApiIOException ignored) {
        }
        String applicationCodeInHeaders = debugServer.getHeader(CustomApiHeaders.APPLICATION_CODE.getValue());

        // then
        assertEquals(10, applicationCodeInHeaders.length());
        assertEquals("0690db1eb3", applicationCodeInHeaders);
        assertEquals("0690db1eb3", MobileMessagingCore.getApplicationCodeHash(context, "TestApplicationCode"));
    }

    @Test
    public void shouldForwardCustomHeadersInRequest() {
        // when
        try {
            mobileApiResourceProvider.getMobileApiVersion(context).getLatestRelease();
            fail();
        } catch (ApiIOException ignored) {
        }

        // then
        assertEquals("0690db1eb3", debugServer.getHeader(CustomApiHeaders.APPLICATION_CODE.getValue()));
        assertEquals("false", debugServer.getHeader(CustomApiHeaders.FOREGROUND.getValue()));
        assertEquals(myDeviceRegId, debugServer.getHeader(CustomApiHeaders.PUSH_REGISTRATION_ID.getValue()));
        assertEquals("UniversalInstallationId", debugServer.getHeader(CustomApiHeaders.INSTALLATION_ID.getValue()));
    }

    @Test
    public void shouldResetBaseUrlOnError() {
        // given
        MobileMessagingCore.setApiUri(context, "http://customurl");

        // when
        try {
            mobileApiResourceProvider.getMobileApiVersion(context).getLatestRelease();
            fail();
        } catch (ApiIOException ignored) {
        }

        // then
        assertEquals(MobileMessagingProperty.API_URI.getDefaultValue(), MobileMessagingCore.getApiUri(context, false));
    }

    @Test
    public void shouldUseNewUrlForSecondRequest() {
        // given
        debugServer.respondWith(NanoHTTPD.Response.Status.OK, null, new HashMap<String, String>() {{
            put(CustomApiHeaders.NEW_BASE_URL.getValue(), "http://customurl");
        }});
        MobileApiVersion givenMobileApiVersion = mobileApiResourceProvider.getMobileApiVersion(context);

        // when
        givenMobileApiVersion.getLatestRelease();
        try {
            givenMobileApiVersion.getLatestRelease();
            fail();
        } catch (ApiIOException e) {
            assertTrue(e.getCause() instanceof UnknownHostException);
            assertTrue(e.getCause().getMessage().contains("customurl"));
        }
    }

    @Test
    public void shouldReplaceNotSupportedChars() {
        Map<Integer, String> unsupportedCharCodes = new HashMap<Integer, String>();
        unsupportedCharCodes.put(0x09,"&x09");
        unsupportedCharCodes.put(0x0a,"&x0a");
        unsupportedCharCodes.put(0x0b,"&x0b");
        unsupportedCharCodes.put(0x0c,"&x0c");
        unsupportedCharCodes.put(0x0d,"&x0d");
        unsupportedCharCodes.put(0x11,"&x11");
        unsupportedCharCodes.put(0x12, "&x12");
        unsupportedCharCodes.put(0x13, "&x13");
        unsupportedCharCodes.put(0x14, "&x14");

        for (int charCode: unsupportedCharCodes.keySet()) {
            char unsupported = (char) charCode;
            String test = "someTest" + unsupported + "testEnd";
            String should = "someTesttestEnd";
            String result = mobileApiResourceProvider.removeNotSupportedChars(test);
            assertEquals(result, should);
        }
    }

}
