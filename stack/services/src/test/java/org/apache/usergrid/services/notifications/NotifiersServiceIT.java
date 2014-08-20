package org.apache.usergrid.services.notifications;

import org.apache.commons.io.IOUtils;
import org.apache.usergrid.services.notifications.apns.MockSuccessfulProviderAdapter;
import org.apache.usergrid.persistence.entities.Notifier;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.apache.usergrid.services.notifications.ConnectionException;
import org.apache.usergrid.services.notifications.NotificationsService;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.SocketException;
import org.apache.usergrid.persistence.Schema;
import org.apache.usergrid.persistence.exceptions.RequiredPropertyNotFoundException;
import org.apache.usergrid.services.AbstractServiceIT;
import org.apache.usergrid.services.ServiceAction;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertArrayEquals;

import static org.junit.Assert.fail;

public class NotifiersServiceIT extends AbstractServiceIT {
    private NotificationsService ns;

    @Before
    public void before() throws Exception {
        ns = (NotificationsService) app.getSm().getService("notifications");
        MockSuccessfulProviderAdapter.install(ns);
    }

    @Test
    public void badProvider() throws Exception {

        app.clear();
        app.put("provider", "xxx");
        app.put("environment", "production");

        try {
            app.testRequest(ServiceAction.POST, 1, "notifiers");
            fail("notifier creation should have failed with a bad provider");
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    @Ignore("Mock doesn't work")
    @Test
    public void badGcmToken() throws Exception {
        app.clear();
        app.put("provider", "google");
        app.put("environment", "xxx");

        try {
            app.testRequest(ServiceAction.POST, 1, "notifiers");
            fail("notifier creation should have failed with missing apiKey");
        } catch (RequiredPropertyNotFoundException e) {
            // ok
        }

        // mock action (based on verified actual behavior) //
        ns.providerAdapters
                .put("google",
                        new org.apache.usergrid.services.notifications.gcm.MockSuccessfulProviderAdapter() {
                            @Override
                            public void testConnection(Notifier notifier)
                                    throws ConnectionException {
                                throw new ConnectionException("", null);
                            }
                        });

        app.put("apiKey", "xxx");

        try {
            app.testRequest(ServiceAction.POST, 1, "notifiers");
            fail("notifier creation should have failed with bad connection");
        } catch (ConnectionException e) {
            // ok
        }
    }

    @Test
    public void badAPNsEnvironment() throws Exception {

        MockSuccessfulProviderAdapter.uninstall(ns);

        app.clear();
        app.put("provider", "apple");
        app.put("environment", "xxx");

        try {
            app.testRequest(ServiceAction.POST, 1, "notifiers");
            fail("notifier creation should have failed with a bad environment");
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    @Test
    public void goodAPNsCreation() throws Exception {

        app.clear();
        app.put("provider", "apple");
        app.put("environment", "development");

        InputStream fis = getClass().getClassLoader().getResourceAsStream(
                "pushtest_dev_recent.p12");
        byte[] certBytes = IOUtils.toByteArray(fis);
        app.put("p12Certificate", certBytes);
        fis.close();

        Notifier notifier = (Notifier) app
                .testRequest(ServiceAction.POST, 1, false,
                        new Object[] { "notifiers" }).getEntity()
                .toTypedEntity();

        assertEquals(app.get("provider"), notifier.getProvider());
        assertEquals(app.get("environment"), notifier.getEnvironment());
        assertArrayEquals(notifier.getP12Certificate(), certBytes);
    }

    @Ignore("Mock doesn't work")
    @Test
    public void badAPNsCertificate() throws Exception {

        // mock error (based on verified actual behavior) //
        ns.providerAdapters.put("apple", new MockSuccessfulProviderAdapter() {
            @Override
            public void testConnection(Notifier notifier)
                    throws ConnectionException {
                Exception e = new SocketException(
                        "Connection closed by remote host");
                throw new ConnectionException(e.getMessage(), e);
            }
        });

        app.clear();
        app.put("provider", "apple");
        app.put("environment", "development");

        InputStream fis = getClass().getClassLoader().getResourceAsStream(
                "pushtest_prod.p12");
        byte[] certBytes = IOUtils.toByteArray(fis);
        app.put("p12Certificate", certBytes);
        fis.close();

        try {
            app.testRequest(ServiceAction.POST, 1, "notifiers");
            fail("notifier creation should have failed with a bad connection test");
        } catch (ConnectionException e) {
            // ok
        }
    }

    @Ignore("Mock doesn't work")
    @Test
    public void badAPNsPassword() throws Exception {

        // mock error (based on verified actual behavior) //
        ns.providerAdapters.put("apple", new MockSuccessfulProviderAdapter() {
            @Override
            public void testConnection(Notifier notifier)
                    throws ConnectionException {
                Exception e = new Exception("invalid ssl config");
                throw new ConnectionException(e.getMessage(), e);
            }
        });

        app.clear();
        app.put("provider", "apple");
        app.put("environment", "development");
        app.put("certificatePassword", "wrong");

        InputStream fis = getClass().getClassLoader().getResourceAsStream(
                "pushtest_dev_recent.p12");
        byte[] certBytes = IOUtils.toByteArray(fis);
        app.put("p12Certificate", certBytes);
        fis.close();

        try {
            app.testRequest(ServiceAction.POST, 1, "notifiers");
            fail("notifier creation should have failed with a bad connection test");
        } catch (ConnectionException e) {
            // ok
        }
    }

    @Test
    public void encryption() throws Exception {

        app.clear();
        app.put("provider", "apple");
        app.put("environment", "development");

        InputStream fis = getClass().getClassLoader().getResourceAsStream(
                "pushtest_dev_recent.p12");
        byte[] certBytes = IOUtils.toByteArray(fis);
        app.put("p12Certificate", certBytes);
        fis.close();

        Field f = Schema.class.getDeclaredField("encryptionSeed");
        f.setAccessible(true);
        byte[] originalSeed = (byte[]) f.get(Schema.class);
        byte[] encryptionSeed = "This is a new seed.".getBytes();
        f.set(Schema.class, encryptionSeed);

        Notifier notifier = (Notifier) app
                .testRequest(ServiceAction.POST, 1, "notifiers").getEntity()
                .toTypedEntity();

        assertArrayEquals(notifier.getP12Certificate(), certBytes);

        f.set(Schema.class, originalSeed);

        try {
            app.getEm().get(notifier.getUuid());
            fail("Should have failed to retrieve the encrypted entity.");
        } catch (IllegalStateException e) {
            // ok! This should happen.
        }
    }
}