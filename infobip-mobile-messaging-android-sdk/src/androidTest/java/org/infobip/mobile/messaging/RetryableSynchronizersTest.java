package org.infobip.mobile.messaging;

import android.annotation.SuppressLint;

import org.infobip.mobile.messaging.api.messages.MobileApiMessages;
import org.infobip.mobile.messaging.api.messages.SyncMessagesBody;
import org.infobip.mobile.messaging.api.support.ApiIOException;
import org.infobip.mobile.messaging.cloud.MobileMessageHandler;
import org.infobip.mobile.messaging.mobile.MobileMessagingError;
import org.infobip.mobile.messaging.mobile.appinstance.InstallationSynchronizer;
import org.infobip.mobile.messaging.mobile.common.MRetryPolicy;
import org.infobip.mobile.messaging.mobile.common.RetryPolicyProvider;
import org.infobip.mobile.messaging.mobile.common.exceptions.BackendCommunicationException;
import org.infobip.mobile.messaging.mobile.messages.MessagesSynchronizer;
import org.infobip.mobile.messaging.mobile.user.UserDataReporter;
import org.infobip.mobile.messaging.stats.MobileMessagingStats;
import org.infobip.mobile.messaging.tools.MobileMessagingTestCase;
import org.infobip.mobile.messaging.util.DeviceInformation;
import org.infobip.mobile.messaging.util.PreferenceHelper;
import org.infobip.mobile.messaging.util.SoftwareInformation;
import org.infobip.mobile.messaging.util.SystemInformation;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author pandric on 08/03/2017.
 */

public class RetryableSynchronizersTest extends MobileMessagingTestCase {

    private Executor executor;

    private MessagesSynchronizer messagesSynchronizer;
    private InstallationSynchronizer installationSynchronizer;
    private UserDataReporter userDataReporter;
    private MRetryPolicy retryPolicy;

    private MobileMessageHandler mobileMessageHandler;

    private MobileApiMessages mobileApiMessages;

    @SuppressLint("CommitPrefEdits")
    @Override
    public void setUp() throws Exception {
        super.setUp();

        MobileMessagingStats stats = mobileMessagingCore.getStats();

        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.REPORT_SYSTEM_INFO, true);
        PreferenceHelper.saveInt(context, MobileMessagingProperty.DEFAULT_EXP_BACKOFF_MULTIPLIER, 0);
        PreferenceHelper.remove(context, MobileMessagingProperty.REPORTED_SYSTEM_DATA_HASH);

        mobileMessageHandler = mock(MobileMessageHandler.class);
        mobileApiMessages = mock(MobileApiMessages.class);

        doThrow(new BackendCommunicationException("Backend error", new ApiIOException("0", "Backend error"))).when(mobileApiAppInstance).patchInstance(anyString(), anyBoolean(), any(Map.class));
        doThrow(new BackendCommunicationException("Backend error", new ApiIOException("0", "Backend error"))).when(mobileApiAppInstance).patchUser(anyString(), anyBoolean(), any(Map.class));
        given(mobileApiMessages.sync(any(SyncMessagesBody.class))).willThrow(new BackendCommunicationException("Backend error", new ApiIOException("0", "Backend error")));

        RetryPolicyProvider retryPolicyProvider = new RetryPolicyProvider(context);
        retryPolicy = retryPolicyProvider.DEFAULT();
        executor = Executors.newSingleThreadExecutor();
        messagesSynchronizer = new MessagesSynchronizer(mobileMessagingCore, stats, executor, broadcaster, retryPolicy, mobileMessageHandler, mobileApiMessages);
        installationSynchronizer = new InstallationSynchronizer(context, mobileMessagingCore, stats, executor, broadcaster, retryPolicyProvider, mobileApiAppInstance);
        userDataReporter = new UserDataReporter(mobileMessagingCore, executor, broadcaster, retryPolicyProvider, stats, mobileApiAppInstance);
    }

    @Test
    public void test_system_data_retry() {

        // Given
        prepareSystemData();

        // When
        installationSynchronizer.sync();

        // Then
        verify(broadcaster, after(3000).times(1)).error(any(MobileMessagingError.class));
        verify(mobileApiAppInstance, times(1 + retryPolicy.getMaxRetries())).patchInstance(anyString(), anyBoolean(), any(Map.class));
    }

    private void prepareSystemData() {
        boolean reportEnabled = PreferenceHelper.findBoolean(context, MobileMessagingProperty.REPORT_SYSTEM_INFO);

        SystemData data = new SystemData(SoftwareInformation.getSDKVersionWithPostfixForSystemData(context),
                reportEnabled ? SystemInformation.getAndroidSystemVersion() : "",
                reportEnabled ? DeviceInformation.getDeviceManufacturer() : "",
                reportEnabled ? DeviceInformation.getDeviceModel() : "",
                reportEnabled ? SoftwareInformation.getAppVersion(context) : "",
                mobileMessagingCore.isGeofencingActivated(),
                SoftwareInformation.areNotificationsEnabled(context),
                DeviceInformation.isDeviceSecure(context),
                reportEnabled ? SystemInformation.getAndroidSystemLanguage() : "",
                reportEnabled ? SystemInformation.getAndroidDeviceName(context) : "");

        Integer hash = PreferenceHelper.findInt(context, MobileMessagingProperty.REPORTED_SYSTEM_DATA_HASH);
        if (hash != data.hashCode()) {
            PreferenceHelper.saveString(context, MobileMessagingProperty.UNREPORTED_SYSTEM_DATA, data.toString());
        }
    }

    @Test
    public void test_sync_messages_retry() {

        // When
        messagesSynchronizer.sync();

        // Then
        verify(broadcaster, after(3000).atLeast(1)).error(any(MobileMessagingError.class));
        verify(mobileApiMessages, times(1 + retryPolicy.getMaxRetries())).sync(any(SyncMessagesBody.class));
    }

    @Test
    public void test_registration_retry() {

        // Given
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.CLOUD_TOKEN_REPORTED, false);

        // When
        installationSynchronizer.sync();

        // Then
        verify(broadcaster, after(3000).times(1)).error(any(MobileMessagingError.class));
        verify(mobileApiAppInstance, times(1 + retryPolicy.getMaxRetries())).patchInstance(anyString(), anyBoolean(), any(Map.class));
    }

    @Test
    public void test_user_data_retry() {

        // Given
        UserData userData = new UserData();
        userData.setFirstName("Retry");
        userData.setLastName("Everything");

        // When
        userDataReporter.sync(null, userData);

        // Then
        verify(broadcaster, after(3000).atLeast(1)).error(any(MobileMessagingError.class));
        verify(mobileApiAppInstance, times(1 + retryPolicy.getMaxRetries())).patchUser(anyString(), anyBoolean(), any(Map.class));
    }

    @Test
    public void test_user_data_opt_out_retry() {

        // Given
        withoutStoringUserData();

        UserData userData = new UserData();
        userData.setFirstName("Retry");
        userData.setLastName("Everything");

        // When
        userDataReporter.sync(null, userData);

        // Then
        verify(broadcaster, after(4000).times(1)).error(any(MobileMessagingError.class));
        verify(mobileApiAppInstance, times(1)).patchUser(anyString(), anyBoolean(), any(Map.class));
    }

    private void withoutStoringUserData() {
        PreferenceHelper.saveBoolean(contextMock, MobileMessagingProperty.SAVE_USER_DATA_ON_DISK, false);
    }
}
