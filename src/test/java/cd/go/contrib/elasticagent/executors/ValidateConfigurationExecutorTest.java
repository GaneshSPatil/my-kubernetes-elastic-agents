/*
 * Copyright 2017 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cd.go.contrib.elasticagent.executors;

import cd.go.contrib.elasticagent.requests.ValidatePluginSettings;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ValidateConfigurationExecutorTest {
    @Test
    public void shouldValidateABadConfiguration() throws Exception {
        ValidatePluginSettings settings = new ValidatePluginSettings();
        GoPluginApiResponse response = new ValidateConfigurationExecutor(settings).execute();

        assertThat(response.responseCode(), is(200));
        JSONAssert.assertEquals("[\n" +
                "  {\n" +
                "    \"message\": \"Go Server URL must not be blank.\",\n" +
                "    \"key\": \"go_server_url\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"message\": \"Agent auto-register Timeout (in minutes) must be a positive integer.\",\n" +
                "    \"key\": \"auto_register_timeout\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"message\": \"Kubernetes Cluster URL must not be blank.\",\n" +
                "    \"key\": \"kubernetes_cluster_url\"\n" +
                "  }\n" +
                "]", response.responseBody(), true);
    }

    @Test
    public void shouldValidateAGoodConfiguration() throws Exception {
        ValidatePluginSettings settings = new ValidatePluginSettings();
        settings.put("go_server_url", "https://ci.example.com");
        settings.put("kubernetes_cluster_url", "https://cluster.example.com");
        settings.put("auto_register_timeout", "10");
        GoPluginApiResponse response = new ValidateConfigurationExecutor(settings).execute();

        assertThat(response.responseCode(), is(200));
        JSONAssert.assertEquals("[]", response.responseBody(), true);
    }
}
