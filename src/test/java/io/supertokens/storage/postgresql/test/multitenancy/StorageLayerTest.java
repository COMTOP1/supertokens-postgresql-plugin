/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.storage.postgresql.test.multitenancy;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.storage.postgresql.Start;
import io.supertokens.storage.postgresql.test.TestingProcessManager;
import io.supertokens.storage.postgresql.test.Utils;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class StorageLayerTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void normalDbConfigErrorContinuesToWork() throws InterruptedException, IOException {
        String[] args = {"../"};

        Utils.setValueInConfig("postgresql_connection_pool_size", "-1");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();

        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals(e.exception.getCause().getMessage(),
                "'postgresql_connection_pool_size' in the config.yaml file must be > 0");

        assertNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE, 1000));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void mergingTenantWithBaseConfigWorks()
            throws InterruptedException, IOException, InvalidConfigException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject tenantConfig = new JsonObject();
        tenantConfig.add("postgresql_table_names_prefix", new JsonPrimitive("test"));

        TenantConfig[] tenants = new TenantConfig[]{
                new TenantConfig("abc", null, new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                        new PasswordlessConfig(false),
                        tenantConfig)};

        Config.loadAllTenantConfig(process.getProcess(), tenants);

        StorageLayer.loadAllTenantStorage(process.getProcess(), tenants);

        Assert.assertEquals(io.supertokens.storage.postgresql.config.Config.getConfig(
                        (Start) StorageLayer.getStorage(null, null, process.getProcess()))
                .getTablePrefix(), "");

        Assert.assertEquals(io.supertokens.storage.postgresql.config.Config.getConfig(
                        (Start) StorageLayer.getStorage("abc", null, process.getProcess()))
                .getTablePrefix(), "test");

        Assert.assertEquals(
                process.getProcess().getResourceDistributor().getAllResourcesWithResourceKey(StorageLayer.RESOURCE_KEY)
                        .size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
//
//    @Test
//    public void mergingTenantWithBaseConfigWithInvalidConfigThrowsErrorWorks()
//            throws InterruptedException, IOException {
//        String[] args = {"../"};
//
//        Utils.setValueInConfig("refresh_token_validity", "144001");
//        Utils.setValueInConfig("access_token_signing_key_dynamic", "false");
//        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
//        FeatureFlagTestContent.getInstance(process.getProcess())
//                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
//        CoreConfigTestContent.getInstance(process.main)
//                .setKeyValue(CoreConfigTestContent.VALIDITY_TESTING, true);
//        process.startProcess();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
//
//        JsonObject tenantConfig = new JsonObject();
//        tenantConfig.add("refresh_token_validity", new JsonPrimitive(1));
//        tenantConfig.add("password_reset_token_lifetime", new JsonPrimitive(3600001));
//
//        try {
//            Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
//                    new TenantConfig("abc", null, new EmailPasswordConfig(false),
//                            new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
//                            new PasswordlessConfig(false),
//                            tenantConfig)});
//            fail();
//        } catch (InvalidConfigException e) {
//            assert (e.getMessage()
//                    .contains("'refresh_token_validity' must be strictly greater than 'access_token_validity'"));
//        }
//
//        process.kill();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
//    }
//
//    @Test
//    public void mergingTenantWithBaseConfigWithConflictingConfigsThrowsError()
//            throws InterruptedException, IOException {
//        String[] args = {"../"};
//
//        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
//        FeatureFlagTestContent.getInstance(process.getProcess())
//                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
//        CoreConfigTestContent.getInstance(process.main)
//                .setKeyValue(CoreConfigTestContent.VALIDITY_TESTING, true);
//        process.startProcess();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
//
//        JsonObject tenantConfig = new JsonObject();
//        tenantConfig.add("access_token_signing_key_dynamic", new JsonPrimitive(false));
//
//        try {
//            Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
//                    new TenantConfig("abc", null, new EmailPasswordConfig(false),
//                            new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
//                            new PasswordlessConfig(false),
//                            tenantConfig)});
//            fail();
//        } catch (InvalidConfigException e) {
//            assert (e.getMessage()
//                    .equals("You cannot set different values for access_token_signing_key_dynamic for the same user
//                    " +
//                            "pool"));
//        }
//
//        process.kill();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
//    }
//
//    @Test
//    public void mergingDifferentUserPoolTenantWithBaseConfigWithConflictingConfigsShouldNotThrowsError()
//            throws InterruptedException, IOException, InvalidConfigException {
//        String[] args = {"../"};
//
//        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
//        FeatureFlagTestContent.getInstance(process.getProcess())
//                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
//        CoreConfigTestContent.getInstance(process.main)
//                .setKeyValue(CoreConfigTestContent.VALIDITY_TESTING, true);
//        process.startProcess();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
//
//        Storage storage = StorageLayer.getStorage(process.getProcess());
//        if (storage.getType() == STORAGE_TYPE.SQL
//                && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
//            JsonObject tenantConfig = new JsonObject();
//
//            if (Version.getVersion(process.getProcess()).getPluginName().equals("postgresql")) {
//                tenantConfig.add("postgresql_database_name", new JsonPrimitive("random"));
//            } else if (Version.getVersion(process.getProcess()).getPluginName().equals("mysql")) {
//                tenantConfig.add("mysql_database_name", new JsonPrimitive("random"));
//            } else {
//                tenantConfig.add("mongodb_connection_uri", new JsonPrimitive("mongodb://root:root@localhost:27018"));
//            }
//            tenantConfig.add("access_token_signing_key_dynamic", new JsonPrimitive(false));
//
//            Config.loadAllTenantConfig(process.getProcess(), new TenantConfig[]{
//                    new TenantConfig("abc", null, new EmailPasswordConfig(false),
//                            new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
//                            new PasswordlessConfig(false),
//                            tenantConfig)});
//
//        }
//
//        Assert.assertEquals(Config.getConfig(process.getProcess()).getAccessTokenSigningKeyDynamic(),
//                true);
//
//        Assert.assertEquals(Config.getConfig("abc", null, process.getProcess()).getPasswordlessMaxCodeInputAttempts(),
//                5);
//        Assert.assertEquals(Config.getConfig("abc", null, process.getProcess()).getAccessTokenSigningKeyDynamic(),
//                false);
//
//        process.kill();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
//    }
//
//    @Test
//    public void testDifferentWaysToGetConfigBasedOnConnectionURIAndTenantId()
//            throws InterruptedException, IOException, InvalidConfigException {
//        String[] args = {"../"};
//
//        Utils.setValueInConfig("refresh_token_validity", "144001");
//        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
//        FeatureFlagTestContent.getInstance(process.getProcess())
//                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
//        process.startProcess();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
//
//        TenantConfig[] tenants = new TenantConfig[4];
//
//        {
//            JsonObject tenantConfig = new JsonObject();
//            tenantConfig.add("refresh_token_validity", new JsonPrimitive(144002));
//            tenants[0] = new TenantConfig("c1", null, new EmailPasswordConfig(false),
//                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
//                    new PasswordlessConfig(false),
//                    tenantConfig);
//        }
//
//        {
//            JsonObject tenantConfig = new JsonObject();
//            tenantConfig.add("refresh_token_validity", new JsonPrimitive(144003));
//            tenants[1] = new TenantConfig("c1", "t1", new EmailPasswordConfig(false),
//                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
//                    new PasswordlessConfig(false),
//                    tenantConfig);
//        }
//
//        {
//            JsonObject tenantConfig = new JsonObject();
//            tenantConfig.add("refresh_token_validity", new JsonPrimitive(144004));
//            tenants[2] = new TenantConfig(null, "t2", new EmailPasswordConfig(false),
//                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
//                    new PasswordlessConfig(false),
//                    tenantConfig);
//        }
//
//        {
//            JsonObject tenantConfig = new JsonObject();
//            tenantConfig.add("refresh_token_validity", new JsonPrimitive(144005));
//            tenants[3] = new TenantConfig(null, "t1", new EmailPasswordConfig(false),
//                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
//                    new PasswordlessConfig(false),
//                    tenantConfig);
//        }
//
//        Config.loadAllTenantConfig(process.getProcess(), tenants);
//
//        Assert.assertEquals(Config.getConfig(null, null, process.getProcess()).getRefreshTokenValidity(),
//                (long) 144001 * 60 * 1000);
//        Assert.assertEquals(Config.getConfig("c1", null, process.getProcess()).getRefreshTokenValidity(),
//                (long) 144002 * 60 * 1000);
//        Assert.assertEquals(Config.getConfig("c1", "t1", process.getProcess()).getRefreshTokenValidity(),
//                (long) 144003 * 60 * 1000);
//        Assert.assertEquals(Config.getConfig(null, "t1", process.getProcess()).getRefreshTokenValidity(),
//                (long) 144005 * 60 * 1000);
//        Assert.assertEquals(Config.getConfig("c2", null, process.getProcess()).getRefreshTokenValidity(),
//                (long) 144001 * 60 * 1000);
//        Assert.assertEquals(Config.getConfig("c2", "t1", process.getProcess()).getRefreshTokenValidity(),
//                (long) 144005 * 60 * 1000);
//        Assert.assertEquals(Config.getConfig("c3", "t2", process.getProcess()).getRefreshTokenValidity(),
//                (long) 144004 * 60 * 1000);
//        Assert.assertEquals(Config.getConfig("c1", "t2", process.getProcess()).getRefreshTokenValidity(),
//                (long) 144002 * 60 * 1000);
//        Assert.assertEquals(Config.getConfig(null, "t2", process.getProcess()).getRefreshTokenValidity(),
//                (long) 144004 * 60 * 1000);
//
//
//        process.kill();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
//    }
}
