/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.biometrics;

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.IActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.biometrics.Authenticator;
import android.hardware.biometrics.BiometricAuthenticator;
import android.hardware.biometrics.BiometricConstants;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.IBiometricAuthenticator;
import android.hardware.biometrics.IBiometricService;
import android.hardware.biometrics.IBiometricServiceReceiver;
import android.hardware.biometrics.IBiometricServiceReceiverInternal;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.security.KeyStore;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.internal.statusbar.IStatusBarService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
public class BiometricServiceTest {

    private static final String TAG = "BiometricServiceTest";

    private static final String TEST_PACKAGE_NAME = "test_package";

    private static final String ERROR_HW_UNAVAILABLE = "hw_unavailable";
    private static final String ERROR_NOT_RECOGNIZED = "not_recognized";
    private static final String ERROR_TIMEOUT = "error_timeout";
    private static final String ERROR_CANCELED = "error_canceled";
    private static final String ERROR_UNABLE_TO_PROCESS = "error_unable_to_process";
    private static final String ERROR_USER_CANCELED = "error_user_canceled";
    private static final String ERROR_LOCKOUT = "error_lockout";

    private static final String FINGERPRINT_ACQUIRED_SENSOR_DIRTY = "sensor_dirty";

    private BiometricService mBiometricService;

    @Mock
    private Context mContext;
    @Mock
    private ContentResolver mContentResolver;
    @Mock
    private Resources mResources;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    IBiometricServiceReceiver mReceiver1;
    @Mock
    IBiometricServiceReceiver mReceiver2;
    @Mock
    BiometricService.Injector mInjector;
    @Mock
    IBiometricAuthenticator mFingerprintAuthenticator;
    @Mock
    IBiometricAuthenticator mFaceAuthenticator;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContext.getResources()).thenReturn(mResources);

        when(mInjector.getActivityManagerService()).thenReturn(mock(IActivityManager.class));
        when(mInjector.getStatusBarService()).thenReturn(mock(IStatusBarService.class));
        when(mInjector.getFingerprintAuthenticator()).thenReturn(mFingerprintAuthenticator);
        when(mInjector.getFaceAuthenticator()).thenReturn(mFaceAuthenticator);
        when(mInjector.getSettingObserver(any(), any(), any())).thenReturn(
                mock(BiometricService.SettingObserver.class));
        when(mInjector.getKeyStore()).thenReturn(mock(KeyStore.class));
        when(mInjector.isDebugEnabled(any(), anyInt())).thenReturn(false);

        when(mResources.getString(R.string.biometric_error_hw_unavailable))
                .thenReturn(ERROR_HW_UNAVAILABLE);
        when(mResources.getString(R.string.biometric_not_recognized))
                .thenReturn(ERROR_NOT_RECOGNIZED);
        when(mResources.getString(R.string.biometric_error_user_canceled))
                .thenReturn(ERROR_USER_CANCELED);
    }

    @Test
    public void testAuthenticate_withoutHardware_returnsErrorHardwareNotPresent() throws
            Exception {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(false);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_IRIS)).thenReturn(false);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(false);

        mBiometricService = new BiometricService(mContext, mInjector);
        mBiometricService.onStart();

        invokeAuthenticate(mBiometricService.mImpl, mReceiver1, false /* requireConfirmation */,
                false /* allowDeviceCredential */);
        waitForIdle();
        verify(mReceiver1).onError(
                eq(BiometricAuthenticator.TYPE_NONE),
                eq(BiometricConstants.BIOMETRIC_ERROR_HW_NOT_PRESENT),
                eq(0 /* vendorCode */));
    }

    @Test
    public void testAuthenticate_withoutEnrolled_returnsErrorNoBiometrics() throws Exception {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)).thenReturn(true);
        when(mFingerprintAuthenticator.isHardwareDetected(any())).thenReturn(true);

        mBiometricService = new BiometricService(mContext, mInjector);
        mBiometricService.onStart();

        invokeAuthenticate(mBiometricService.mImpl, mReceiver1, false /* requireConfirmation */,
                false /* allowDeviceCredential */);
        waitForIdle();
        verify(mReceiver1).onError(
                eq(BiometricAuthenticator.TYPE_FINGERPRINT),
                eq(BiometricConstants.BIOMETRIC_ERROR_NO_BIOMETRICS),
                eq(0 /* vendorCode */));
    }

    @Test
    public void testAuthenticate_whenHalIsDead_returnsErrorHardwareUnavailable() throws
            Exception {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)).thenReturn(true);
        when(mFingerprintAuthenticator.hasEnrolledTemplates(anyInt(), any())).thenReturn(true);
        when(mFingerprintAuthenticator.isHardwareDetected(any())).thenReturn(false);

        mBiometricService = new BiometricService(mContext, mInjector);
        mBiometricService.onStart();

        invokeAuthenticate(mBiometricService.mImpl, mReceiver1, false /* requireConfirmation */,
                false /* allowDeviceCredential */);
        waitForIdle();
        verify(mReceiver1).onError(
                eq(BiometricAuthenticator.TYPE_NONE),
                eq(BiometricConstants.BIOMETRIC_ERROR_HW_UNAVAILABLE),
                eq(0 /* vendorCode */));
    }

    @Test
    public void testAuthenticateFace_respectsUserSetting()
            throws Exception {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
        when(mFaceAuthenticator.hasEnrolledTemplates(anyInt(), any())).thenReturn(true);
        when(mFaceAuthenticator.isHardwareDetected(any())).thenReturn(true);

        mBiometricService = new BiometricService(mContext, mInjector);
        mBiometricService.onStart();

        // Disabled in user settings receives onError
        when(mBiometricService.mSettingObserver.getFaceEnabledForApps(anyInt())).thenReturn(false);
        invokeAuthenticate(mBiometricService.mImpl, mReceiver1, false /* requireConfirmation */,
                false /* allowDeviceCredential */);
        waitForIdle();
        verify(mReceiver1).onError(
                eq(BiometricAuthenticator.TYPE_NONE),
                eq(BiometricConstants.BIOMETRIC_ERROR_HW_UNAVAILABLE),
                eq(0 /* vendorCode */));

        // Enrolled, not disabled in settings, user requires confirmation in settings
        resetReceiver();
        when(mBiometricService.mSettingObserver.getFaceEnabledForApps(anyInt())).thenReturn(true);
        when(mBiometricService.mSettingObserver.getFaceAlwaysRequireConfirmation(anyInt()))
                .thenReturn(true);
        invokeAuthenticate(mBiometricService.mImpl, mReceiver1, false /* requireConfirmation */,
                false /* allowDeviceCredential */);
        waitForIdle();
        verify(mReceiver1, never()).onError(anyInt(), anyInt(), anyInt());
        verify(mBiometricService.mAuthenticators.get(0).impl).prepareForAuthentication(
                eq(true) /* requireConfirmation */,
                any(IBinder.class),
                anyLong() /* sessionId */,
                anyInt() /* userId */,
                any(IBiometricServiceReceiverInternal.class),
                anyString() /* opPackageName */,
                anyInt() /* cookie */,
                anyInt() /* callingUid */,
                anyInt() /* callingPid */,
                anyInt() /* callingUserId */);

        // Enrolled, not disabled in settings, user doesn't require confirmation in settings
        resetReceiver();
        when(mBiometricService.mSettingObserver.getFaceAlwaysRequireConfirmation(anyInt()))
                .thenReturn(false);
        invokeAuthenticate(mBiometricService.mImpl, mReceiver1, false /* requireConfirmation */,
                false /* allowDeviceCredential */);
        waitForIdle();
        verify(mBiometricService.mAuthenticators.get(0).impl).prepareForAuthentication(
                eq(false) /* requireConfirmation */,
                any(IBinder.class),
                anyLong() /* sessionId */,
                anyInt() /* userId */,
                any(IBiometricServiceReceiverInternal.class),
                anyString() /* opPackageName */,
                anyInt() /* cookie */,
                anyInt() /* callingUid */,
                anyInt() /* callingPid */,
                anyInt() /* callingUserId */);
    }

    @Test
    public void testAuthenticate_happyPathWithoutConfirmation() throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FINGERPRINT);
        mBiometricService = new BiometricService(mContext, mInjector);
        mBiometricService.onStart();

        // Start testing the happy path
        invokeAuthenticate(mBiometricService.mImpl, mReceiver1, false /* requireConfirmation */,
                false /* allowDeviceCredential */);
        waitForIdle();

        // Creates a pending auth session with the correct initial states
        assertEquals(mBiometricService.mPendingAuthSession.mState,
                BiometricService.STATE_AUTH_CALLED);

        // Invokes <Modality>Service#prepareForAuthentication
        ArgumentCaptor<Integer> cookieCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mReceiver1, never()).onError(anyInt(), anyInt(), anyInt());
        verify(mBiometricService.mAuthenticators.get(0).impl).prepareForAuthentication(
                anyBoolean() /* requireConfirmation */,
                any(IBinder.class),
                anyLong() /* sessionId */,
                anyInt() /* userId */,
                any(IBiometricServiceReceiverInternal.class),
                anyString() /* opPackageName */,
                cookieCaptor.capture() /* cookie */,
                anyInt() /* callingUid */,
                anyInt() /* callingPid */,
                anyInt() /* callingUserId */);

        // onReadyForAuthentication, mCurrentAuthSession state OK
        mBiometricService.mImpl.onReadyForAuthentication(cookieCaptor.getValue(),
                anyBoolean() /* requireConfirmation */, anyInt() /* userId */);
        waitForIdle();
        assertNull(mBiometricService.mPendingAuthSession);
        assertEquals(mBiometricService.mCurrentAuthSession.mState,
                BiometricService.STATE_AUTH_STARTED);

        // startPreparedClient invoked
        verify(mBiometricService.mAuthenticators.get(0).impl)
                .startPreparedClient(cookieCaptor.getValue());

        // StatusBar showBiometricDialog invoked
        verify(mBiometricService.mStatusBarService).showAuthenticationDialog(
                eq(mBiometricService.mCurrentAuthSession.mBundle),
                any(IBiometricServiceReceiverInternal.class),
                eq(BiometricAuthenticator.TYPE_FINGERPRINT),
                anyBoolean() /* requireConfirmation */,
                anyInt() /* userId */,
                eq(TEST_PACKAGE_NAME));

        // Hardware authenticated
        mBiometricService.mInternalReceiver.onAuthenticationSucceeded(
                false /* requireConfirmation */,
                new byte[69] /* HAT */);
        waitForIdle();
        // Waiting for SystemUI to send dismissed callback
        assertEquals(mBiometricService.mCurrentAuthSession.mState,
                BiometricService.STATE_AUTHENTICATED_PENDING_SYSUI);
        // Notify SystemUI hardware authenticated
        verify(mBiometricService.mStatusBarService).onBiometricAuthenticated();

        // SystemUI sends callback with dismissed reason
        mBiometricService.mInternalReceiver.onDialogDismissed(
                BiometricPrompt.DISMISSED_REASON_BIOMETRIC_CONFIRM_NOT_REQUIRED);
        waitForIdle();
        // HAT sent to keystore
        verify(mBiometricService.mKeyStore).addAuthToken(any(byte[].class));
        // Send onAuthenticated to client
        verify(mReceiver1).onAuthenticationSucceeded();
        // Current session becomes null
        assertNull(mBiometricService.mCurrentAuthSession);
    }

    @Test
    public void testAuthenticate_noBiometrics_credentialAllowed() throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FACE);
        when(mFaceAuthenticator.hasEnrolledTemplates(anyInt(), any())).thenReturn(false);
        invokeAuthenticate(mBiometricService.mImpl, mReceiver1,
                true /* requireConfirmation */, true /* allowDeviceCredential */);
        waitForIdle();

        assertEquals(BiometricService.STATE_SHOWING_DEVICE_CREDENTIAL,
                mBiometricService.mCurrentAuthSession.mState);
        assertEquals(Authenticator.TYPE_CREDENTIAL,
                mBiometricService.mCurrentAuthSession.mBundle
                        .getInt(BiometricPrompt.KEY_AUTHENTICATORS_ALLOWED));
        verify(mBiometricService.mStatusBarService).showAuthenticationDialog(
                eq(mBiometricService.mCurrentAuthSession.mBundle),
                any(IBiometricServiceReceiverInternal.class),
                eq(0 /* biometricModality */),
                anyBoolean() /* requireConfirmation */,
                anyInt() /* userId */,
                eq(TEST_PACKAGE_NAME));
    }

    @Test
    public void testAuthenticate_happyPathWithConfirmation() throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FACE);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                true /* requireConfirmation */, false /* allowDeviceCredential */);

        // Test authentication succeeded goes to PENDING_CONFIRMATION and that the HAT is not
        // sent to KeyStore yet
        mBiometricService.mInternalReceiver.onAuthenticationSucceeded(
                true /* requireConfirmation */,
                new byte[69] /* HAT */);
        waitForIdle();
        // Waiting for SystemUI to send confirmation callback
        assertEquals(mBiometricService.mCurrentAuthSession.mState,
                BiometricService.STATE_AUTH_PENDING_CONFIRM);
        verify(mBiometricService.mKeyStore, never()).addAuthToken(any(byte[].class));

        // SystemUI sends confirm, HAT is sent to keystore and client is notified.
        mBiometricService.mInternalReceiver.onDialogDismissed(
                BiometricPrompt.DISMISSED_REASON_BIOMETRIC_CONFIRMED);
        waitForIdle();
        verify(mBiometricService.mKeyStore).addAuthToken(any(byte[].class));
        verify(mReceiver1).onAuthenticationSucceeded();
    }

    @Test
    public void testRejectFace_whenAuthenticating_notifiesSystemUIAndClient_thenPaused()
            throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FACE);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, false /* allowDeviceCredential */);

        mBiometricService.mInternalReceiver.onAuthenticationFailed();
        waitForIdle();

        verify(mBiometricService.mStatusBarService).onBiometricError(
                eq(BiometricAuthenticator.TYPE_NONE),
                eq(BiometricConstants.BIOMETRIC_PAUSED_REJECTED),
                eq(0 /* vendorCode */));
        verify(mReceiver1).onAuthenticationFailed();
        assertEquals(mBiometricService.mCurrentAuthSession.mState,
                BiometricService.STATE_AUTH_PAUSED);
    }

    @Test
    public void testRejectFingerprint_whenAuthenticating_notifiesAndKeepsAuthenticating()
            throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FINGERPRINT);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, false /* allowDeviceCredential */);

        mBiometricService.mInternalReceiver.onAuthenticationFailed();
        waitForIdle();

        verify(mBiometricService.mStatusBarService).onBiometricError(
                eq(BiometricAuthenticator.TYPE_NONE),
                eq(BiometricConstants.BIOMETRIC_PAUSED_REJECTED),
                eq(0 /* vendorCode */));
        verify(mReceiver1).onAuthenticationFailed();
        assertEquals(mBiometricService.mCurrentAuthSession.mState,
                BiometricService.STATE_AUTH_STARTED);
    }

    @Test
    public void testErrorCanceled_whenAuthenticating_notifiesSystemUIAndClient() throws
            Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FINGERPRINT);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, false /* allowDeviceCredential */);

        // Create a new pending auth session but don't start it yet. HAL contract is that previous
        // one must get ERROR_CANCELED. Simulate that here by creating the pending auth session,
        // sending ERROR_CANCELED to the current auth session, and then having the second one
        // onReadyForAuthentication.
        invokeAuthenticate(mBiometricService.mImpl, mReceiver2, false /* requireConfirmation */,
                false /* allowDeviceCredential */);
        waitForIdle();

        assertEquals(mBiometricService.mCurrentAuthSession.mState,
                BiometricService.STATE_AUTH_STARTED);
        mBiometricService.mInternalReceiver.onError(
                getCookieForCurrentSession(mBiometricService.mCurrentAuthSession),
                BiometricAuthenticator.TYPE_FINGERPRINT,
                BiometricConstants.BIOMETRIC_ERROR_CANCELED, 0 /* vendorCode */);
        waitForIdle();

        // Auth session doesn't become null until SystemUI responds that the animation is completed
        assertNotNull(mBiometricService.mCurrentAuthSession);
        // ERROR_CANCELED is not sent until SystemUI responded that animation is completed
        verify(mReceiver1, never()).onError(anyInt(), anyInt(), anyInt());
        verify(mReceiver2, never()).onError(anyInt(), anyInt(), anyInt());

        // SystemUI dialog closed
        verify(mBiometricService.mStatusBarService).hideAuthenticationDialog();

        // After SystemUI notifies that the animation has completed
        mBiometricService.mInternalReceiver
                .onDialogDismissed(BiometricPrompt.DISMISSED_REASON_SERVER_REQUESTED);
        waitForIdle();
        verify(mReceiver1).onError(
                eq(BiometricAuthenticator.TYPE_FINGERPRINT),
                eq(BiometricConstants.BIOMETRIC_ERROR_CANCELED),
                eq(0 /* vendorCode */));
        assertNull(mBiometricService.mCurrentAuthSession);
    }

    @Test
    public void testErrorHalTimeout_whenAuthenticating_entersPausedState() throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FACE);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, false /* allowDeviceCredential */);

        mBiometricService.mInternalReceiver.onError(
                getCookieForCurrentSession(mBiometricService.mCurrentAuthSession),
                BiometricAuthenticator.TYPE_FACE,
                BiometricConstants.BIOMETRIC_ERROR_TIMEOUT,
                0 /* vendorCode */);
        waitForIdle();

        assertEquals(mBiometricService.mCurrentAuthSession.mState,
                BiometricService.STATE_AUTH_PAUSED);
        verify(mBiometricService.mStatusBarService).onBiometricError(
                eq(BiometricAuthenticator.TYPE_FACE),
                eq(BiometricConstants.BIOMETRIC_ERROR_TIMEOUT),
                eq(0 /* vendorCode */));
        // Timeout does not count as fail as per BiometricPrompt documentation.
        verify(mReceiver1, never()).onAuthenticationFailed();

        // No pending auth session. Pressing try again will create one.
        assertNull(mBiometricService.mPendingAuthSession);

        // Pressing "Try again" on SystemUI starts a new auth session.
        mBiometricService.mInternalReceiver.onTryAgainPressed();
        waitForIdle();

        // The last one is still paused, and a new one has been created.
        assertEquals(mBiometricService.mCurrentAuthSession.mState,
                BiometricService.STATE_AUTH_PAUSED);
        assertEquals(mBiometricService.mPendingAuthSession.mState,
                BiometricService.STATE_AUTH_CALLED);

        // Test resuming when hardware becomes ready. SystemUI should not be requested to
        // show another dialog since it's already showing.
        resetStatusBar();
        startPendingAuthSession(mBiometricService);
        waitForIdle();
        verify(mBiometricService.mStatusBarService, never()).showAuthenticationDialog(
                any(Bundle.class),
                any(IBiometricServiceReceiverInternal.class),
                anyInt(),
                anyBoolean() /* requireConfirmation */,
                anyInt() /* userId */,
                anyString());
    }

    @Test
    public void testErrorFromHal_whenPaused_notifiesSystemUIAndClient() throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FACE);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, false /* allowDeviceCredential */);

        mBiometricService.mInternalReceiver.onError(
                getCookieForCurrentSession(mBiometricService.mCurrentAuthSession),
                BiometricAuthenticator.TYPE_FACE,
                BiometricConstants.BIOMETRIC_ERROR_TIMEOUT,
                0 /* vendorCode */);
        mBiometricService.mInternalReceiver.onError(
                getCookieForCurrentSession(mBiometricService.mCurrentAuthSession),
                BiometricAuthenticator.TYPE_FACE,
                BiometricConstants.BIOMETRIC_ERROR_CANCELED,
                0 /* vendorCode */);
        waitForIdle();

        // Client receives error immediately
        verify(mReceiver1).onError(
                eq(BiometricAuthenticator.TYPE_FACE),
                eq(BiometricConstants.BIOMETRIC_ERROR_CANCELED),
                eq(0 /* vendorCode */));
        // Dialog is hidden immediately
        verify(mBiometricService.mStatusBarService).hideAuthenticationDialog();
        // Auth session is over
        assertNull(mBiometricService.mCurrentAuthSession);
    }

    @Test
    public void testErrorFromHal_whileAuthenticating_waitsForSysUIBeforeNotifyingClient()
            throws Exception {
        // For errors that show in SystemUI, BiometricService stays in STATE_ERROR_PENDING_SYSUI
        // until SystemUI notifies us that the dialog is dismissed at which point the current
        // session is done.
        setupAuthForOnly(BiometricAuthenticator.TYPE_FINGERPRINT);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, false /* allowDeviceCredential */);

        mBiometricService.mInternalReceiver.onError(
                getCookieForCurrentSession(mBiometricService.mCurrentAuthSession),
                BiometricAuthenticator.TYPE_FINGERPRINT,
                BiometricConstants.BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                0 /* vendorCode */);
        waitForIdle();

        // Sends error to SystemUI and does not notify client yet
        assertEquals(mBiometricService.mCurrentAuthSession.mState,
                BiometricService.STATE_ERROR_PENDING_SYSUI);
        verify(mBiometricService.mStatusBarService).onBiometricError(
                eq(BiometricAuthenticator.TYPE_FINGERPRINT),
                eq(BiometricConstants.BIOMETRIC_ERROR_UNABLE_TO_PROCESS),
                eq(0 /* vendorCode */));
        verify(mBiometricService.mStatusBarService, never()).hideAuthenticationDialog();
        verify(mReceiver1, never()).onError(anyInt(), anyInt(), anyInt());

        // SystemUI animation completed, client is notified, auth session is over
        mBiometricService.mInternalReceiver
                .onDialogDismissed(BiometricPrompt.DISMISSED_REASON_ERROR);
        waitForIdle();
        verify(mReceiver1).onError(
                eq(BiometricAuthenticator.TYPE_FINGERPRINT),
                eq(BiometricConstants.BIOMETRIC_ERROR_UNABLE_TO_PROCESS),
                eq(0 /* vendorCode */));
        assertNull(mBiometricService.mCurrentAuthSession);
    }

    @Test
    public void testErrorFromHal_whilePreparingAuthentication_credentialAllowed() throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FINGERPRINT);
        invokeAuthenticate(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, true /* allowDeviceCredential */);
        waitForIdle();

        mBiometricService.mInternalReceiver.onError(
                getCookieForPendingSession(mBiometricService.mPendingAuthSession),
                BiometricAuthenticator.TYPE_FACE,
                BiometricConstants.BIOMETRIC_ERROR_LOCKOUT,
                0 /* vendorCode */);
        waitForIdle();

        // Pending auth session becomes current auth session, since device credential should
        // be shown now.
        assertNull(mBiometricService.mPendingAuthSession);
        assertNotNull(mBiometricService.mCurrentAuthSession);
        assertEquals(BiometricService.STATE_SHOWING_DEVICE_CREDENTIAL,
                mBiometricService.mCurrentAuthSession.mState);
        assertEquals(Authenticator.TYPE_CREDENTIAL,
                mBiometricService.mCurrentAuthSession.mBundle.getInt(
                        BiometricPrompt.KEY_AUTHENTICATORS_ALLOWED));
        verify(mBiometricService.mStatusBarService).showAuthenticationDialog(
                eq(mBiometricService.mCurrentAuthSession.mBundle),
                any(IBiometricServiceReceiverInternal.class),
                eq(0 /* biometricModality */),
                anyBoolean() /* requireConfirmation */,
                anyInt() /* userId */,
                eq(TEST_PACKAGE_NAME));
    }

    @Test
    public void testErrorFromHal_whilePreparingAuthentication_credentialNotAllowed()
            throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FINGERPRINT);
        invokeAuthenticate(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, false /* allowDeviceCredential */);
        waitForIdle();

        mBiometricService.mInternalReceiver.onError(
                getCookieForPendingSession(mBiometricService.mPendingAuthSession),
                BiometricAuthenticator.TYPE_FINGERPRINT,
                BiometricConstants.BIOMETRIC_ERROR_LOCKOUT,
                0 /* vendorCode */);
        waitForIdle();

        // Error is sent to client
        assertNull(mBiometricService.mPendingAuthSession);
        assertNull(mBiometricService.mCurrentAuthSession);
    }

    @Test
    public void testCombineAuthenticatorBundle_keyAllowDeviceCredentialAlwaysRemoved() {
        Bundle bundle;
        int authenticators;

        // In:
        // KEY_ALLOW_DEVICE_CREDENTIAL = true
        // KEY_AUTHENTICATORS_ALLOWED = TYPE_BIOMETRIC | TYPE_CREDENTIAL
        // Out:
        // KEY_ALLOW_DEVICE_CREDENTIAL = null
        // KEY_AUTHENTICATORS_ALLOWED = TYPE_BIOMETRIC | TYPE_CREDENTIAL
        bundle = new Bundle();
        bundle.putBoolean(BiometricPrompt.KEY_ALLOW_DEVICE_CREDENTIAL, true);
        authenticators = Authenticator.TYPE_CREDENTIAL | Authenticator.TYPE_BIOMETRIC;
        bundle.putInt(BiometricPrompt.KEY_AUTHENTICATORS_ALLOWED, authenticators);
        Utils.combineAuthenticatorBundles(bundle);
        assertNull(bundle.get(BiometricPrompt.KEY_ALLOW_DEVICE_CREDENTIAL));
        assertEquals(authenticators, bundle.getInt(BiometricPrompt.KEY_AUTHENTICATORS_ALLOWED));

        // In:
        // KEY_ALLOW_DEVICE_CREDENTIAL = true
        // KEY_AUTHENTICATORS_ALLOWED = TYPE_BIOMETRIC
        // Out:
        // KEY_ALLOW_DEVICE_CREDENTIAL = null
        // KEY_AUTHENTICATORS_ALLOWED = TYPE_BIOMETRIC | TYPE_CREDENTIAL
        bundle = new Bundle();
        bundle.putBoolean(BiometricPrompt.KEY_ALLOW_DEVICE_CREDENTIAL, true);
        authenticators = Authenticator.TYPE_BIOMETRIC;
        bundle.putInt(BiometricPrompt.KEY_AUTHENTICATORS_ALLOWED, authenticators);
        Utils.combineAuthenticatorBundles(bundle);
        assertNull(bundle.get(BiometricPrompt.KEY_ALLOW_DEVICE_CREDENTIAL));
        assertEquals(authenticators, bundle.getInt(BiometricPrompt.KEY_AUTHENTICATORS_ALLOWED));

        // In:
        // KEY_ALLOW_DEVICE_CREDENTIAL = null
        // KEY_AUTHENTICATORS_ALLOWED = TYPE_BIOMETRIC | TYPE_CREDENTIAL
        // Out:
        // KEY_ALLOW_DEVICE_CREDENTIAL = null
        // KEY_AUTHENTICATORS_ALLOWED = TYPE_BIOMETRIC | TYPE_CREDENTIAL
        bundle = new Bundle();
        authenticators = Authenticator.TYPE_BIOMETRIC | Authenticator.TYPE_CREDENTIAL;
        bundle.putInt(BiometricPrompt.KEY_AUTHENTICATORS_ALLOWED, authenticators);
        Utils.combineAuthenticatorBundles(bundle);
        assertNull(bundle.get(BiometricPrompt.KEY_ALLOW_DEVICE_CREDENTIAL));
        assertEquals(authenticators, bundle.getInt(BiometricPrompt.KEY_AUTHENTICATORS_ALLOWED));
    }

    @Test
    public void testErrorFromHal_whileShowingDeviceCredential_doesntNotifySystemUI()
            throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FINGERPRINT);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, true /* allowDeviceCredential */);

        mBiometricService.mInternalReceiver.onDeviceCredentialPressed();
        waitForIdle();

        assertEquals(BiometricService.STATE_SHOWING_DEVICE_CREDENTIAL,
                mBiometricService.mCurrentAuthSession.mState);
        verify(mReceiver1, never()).onError(anyInt(), anyInt(), anyInt());

        mBiometricService.mInternalReceiver.onError(
                getCookieForCurrentSession(mBiometricService.mCurrentAuthSession),
                BiometricAuthenticator.TYPE_FINGERPRINT,
                BiometricConstants.BIOMETRIC_ERROR_CANCELED,
                0 /* vendorCode */);
        waitForIdle();

        assertEquals(BiometricService.STATE_SHOWING_DEVICE_CREDENTIAL,
                mBiometricService.mCurrentAuthSession.mState);
        verify(mReceiver1, never()).onError(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void testLockout_whileAuthenticating_credentialAllowed() throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FINGERPRINT);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, true /* allowDeviceCredential */);

        assertEquals(BiometricService.STATE_AUTH_STARTED,
                mBiometricService.mCurrentAuthSession.mState);

        mBiometricService.mInternalReceiver.onError(
                getCookieForCurrentSession(mBiometricService.mCurrentAuthSession),
                BiometricAuthenticator.TYPE_FINGERPRINT,
                BiometricConstants.BIOMETRIC_ERROR_LOCKOUT,
                0 /* vendorCode */);
        waitForIdle();

        assertEquals(BiometricService.STATE_SHOWING_DEVICE_CREDENTIAL,
                mBiometricService.mCurrentAuthSession.mState);
        verify(mBiometricService.mStatusBarService).onBiometricError(
                eq(BiometricAuthenticator.TYPE_FINGERPRINT),
                eq(BiometricConstants.BIOMETRIC_ERROR_LOCKOUT),
                eq(0 /* vendorCode */));
    }

    @Test
    public void testLockout_whenAuthenticating_credentialNotAllowed() throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FINGERPRINT);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, false /* allowDeviceCredential */);

        assertEquals(BiometricService.STATE_AUTH_STARTED,
                mBiometricService.mCurrentAuthSession.mState);

        mBiometricService.mInternalReceiver.onError(
                getCookieForCurrentSession(mBiometricService.mCurrentAuthSession),
                BiometricAuthenticator.TYPE_FINGERPRINT,
                BiometricConstants.BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                0 /* vendorCode */);
        waitForIdle();

        assertEquals(BiometricService.STATE_ERROR_PENDING_SYSUI,
                mBiometricService.mCurrentAuthSession.mState);
        verify(mBiometricService.mStatusBarService).onBiometricError(
                eq(BiometricAuthenticator.TYPE_FINGERPRINT),
                eq(BiometricConstants.BIOMETRIC_ERROR_UNABLE_TO_PROCESS),
                eq(0 /* vendorCode */));
    }

    @Test
    public void testDismissedReasonUserCancel_whileAuthenticating_cancelsHalAuthentication()
            throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FINGERPRINT);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, false /* allowDeviceCredential */);

        mBiometricService.mInternalReceiver
                .onDialogDismissed(BiometricPrompt.DISMISSED_REASON_USER_CANCEL);
        waitForIdle();
        verify(mReceiver1).onError(
                eq(BiometricAuthenticator.TYPE_FINGERPRINT),
                eq(BiometricConstants.BIOMETRIC_ERROR_USER_CANCELED),
                eq(0 /* vendorCode */));
        verify(mBiometricService.mAuthenticators.get(0).impl).cancelAuthenticationFromService(
                any(),
                any(),
                anyInt(),
                anyInt(),
                anyInt(),
                eq(false) /* fromClient */);
        assertNull(mBiometricService.mCurrentAuthSession);
    }

    @Test
    public void testDismissedReasonNegative_whilePaused_doesntInvokeHalCancel() throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FACE);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, false /* allowDeviceCredential */);

        mBiometricService.mInternalReceiver.onError(
                getCookieForCurrentSession(mBiometricService.mCurrentAuthSession),
                BiometricAuthenticator.TYPE_FACE,
                BiometricConstants.BIOMETRIC_ERROR_TIMEOUT,
                0 /* vendorCode */);
        mBiometricService.mInternalReceiver.onDialogDismissed(
                BiometricPrompt.DISMISSED_REASON_NEGATIVE);
        waitForIdle();

        verify(mBiometricService.mAuthenticators.get(0).impl,
                never()).cancelAuthenticationFromService(
                any(),
                any(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyBoolean());
    }

    @Test
    public void testDismissedReasonUserCancel_whilePaused_doesntInvokeHalCancel() throws
            Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FACE);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, false /* allowDeviceCredential */);

        mBiometricService.mInternalReceiver.onError(
                getCookieForCurrentSession(mBiometricService.mCurrentAuthSession),
                BiometricAuthenticator.TYPE_FACE,
                BiometricConstants.BIOMETRIC_ERROR_TIMEOUT,
                0 /* vendorCode */);
        mBiometricService.mInternalReceiver.onDialogDismissed(
                BiometricPrompt.DISMISSED_REASON_USER_CANCEL);
        waitForIdle();

        verify(mBiometricService.mAuthenticators.get(0).impl,
                never()).cancelAuthenticationFromService(
                any(),
                any(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyBoolean());
    }

    @Test
    public void testDismissedReasonUserCancel_whenPendingConfirmation() throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FACE);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                true /* requireConfirmation */, false /* allowDeviceCredential */);

        mBiometricService.mInternalReceiver.onAuthenticationSucceeded(
                true /* requireConfirmation */,
                new byte[69] /* HAT */);
        mBiometricService.mInternalReceiver.onDialogDismissed(
                BiometricPrompt.DISMISSED_REASON_USER_CANCEL);
        waitForIdle();

        // doesn't send cancel to HAL
        verify(mBiometricService.mAuthenticators.get(0).impl,
                never()).cancelAuthenticationFromService(
                any(),
                any(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyBoolean());
        verify(mReceiver1).onError(
                eq(BiometricAuthenticator.TYPE_FACE),
                eq(BiometricConstants.BIOMETRIC_ERROR_USER_CANCELED),
                eq(0 /* vendorCode */));
        assertNull(mBiometricService.mCurrentAuthSession);
    }

    @Test
    public void testAcquire_whenAuthenticating_sentToSystemUI() throws Exception {
        setupAuthForOnly(BiometricAuthenticator.TYPE_FINGERPRINT);
        invokeAuthenticateAndStart(mBiometricService.mImpl, mReceiver1,
                false /* requireConfirmation */, false /* allowDeviceCredential */);

        mBiometricService.mInternalReceiver.onAcquired(
                FingerprintManager.FINGERPRINT_ACQUIRED_IMAGER_DIRTY,
                FINGERPRINT_ACQUIRED_SENSOR_DIRTY);
        waitForIdle();

        // Sends to SysUI and stays in authenticating state
        verify(mBiometricService.mStatusBarService)
                .onBiometricHelp(eq(FINGERPRINT_ACQUIRED_SENSOR_DIRTY));
        assertEquals(mBiometricService.mCurrentAuthSession.mState,
                BiometricService.STATE_AUTH_STARTED);
    }

    // Helper methods

    private void setupAuthForOnly(int modality) throws RemoteException {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                .thenReturn(false);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(false);

        if (modality == BiometricAuthenticator.TYPE_FINGERPRINT) {
            when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                    .thenReturn(true);
            when(mFingerprintAuthenticator.hasEnrolledTemplates(anyInt(), any())).thenReturn(true);
            when(mFingerprintAuthenticator.isHardwareDetected(any())).thenReturn(true);
        } else if (modality == BiometricAuthenticator.TYPE_FACE) {
            when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE)).thenReturn(true);
            when(mFaceAuthenticator.hasEnrolledTemplates(anyInt(), any())).thenReturn(true);
            when(mFaceAuthenticator.isHardwareDetected(any())).thenReturn(true);
        } else {
            fail("Unknown modality: " + modality);
        }

        mBiometricService = new BiometricService(mContext, mInjector);
        mBiometricService.onStart();

        when(mBiometricService.mSettingObserver.getFaceEnabledForApps(anyInt())).thenReturn(true);
    }

    private void resetReceiver() {
        mReceiver1 = mock(IBiometricServiceReceiver.class);
        mReceiver2 = mock(IBiometricServiceReceiver.class);
    }

    private void resetStatusBar() {
        mBiometricService.mStatusBarService = mock(IStatusBarService.class);
    }

    private void invokeAuthenticateAndStart(IBiometricService.Stub service,
            IBiometricServiceReceiver receiver, boolean requireConfirmation,
            boolean allowDeviceCredential) throws Exception {
        // Request auth, creates a pending session
        invokeAuthenticate(service, receiver, requireConfirmation, allowDeviceCredential);
        waitForIdle();

        startPendingAuthSession(mBiometricService);
        waitForIdle();
    }

    private static void startPendingAuthSession(BiometricService service) throws Exception {
        // Get the cookie so we can pretend the hardware is ready to authenticate
        // Currently we only support single modality per auth
        assertEquals(service.mPendingAuthSession.mModalitiesWaiting.values().size(), 1);
        final int cookie = service.mPendingAuthSession.mModalitiesWaiting.values()
                .iterator().next();
        assertNotEquals(cookie, 0);

        service.mImpl.onReadyForAuthentication(cookie,
                anyBoolean() /* requireConfirmation */, anyInt() /* userId */);
    }

    private static void invokeAuthenticate(IBiometricService.Stub service,
            IBiometricServiceReceiver receiver, boolean requireConfirmation,
            boolean allowDeviceCredential) throws Exception {
        service.authenticate(
                new Binder() /* token */,
                0 /* sessionId */,
                0 /* userId */,
                receiver,
                TEST_PACKAGE_NAME /* packageName */,
                createTestBiometricPromptBundle(requireConfirmation, allowDeviceCredential));
    }

    private static Bundle createTestBiometricPromptBundle(boolean requireConfirmation,
            boolean allowDeviceCredential) {
        final Bundle bundle = new Bundle();
        bundle.putBoolean(BiometricPrompt.KEY_REQUIRE_CONFIRMATION, requireConfirmation);

        if (allowDeviceCredential) {
            bundle.putBoolean(BiometricPrompt.KEY_ALLOW_DEVICE_CREDENTIAL, true);
        }
        return bundle;
    }

    private static int getCookieForCurrentSession(BiometricService.AuthSession session) {
        assertEquals(session.mModalitiesMatched.values().size(), 1);
        return session.mModalitiesMatched.values().iterator().next();
    }

    private static int getCookieForPendingSession(BiometricService.AuthSession session) {
        assertEquals(session.mModalitiesWaiting.values().size(), 1);
        return session.mModalitiesWaiting.values().iterator().next();
    }

    private static void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
