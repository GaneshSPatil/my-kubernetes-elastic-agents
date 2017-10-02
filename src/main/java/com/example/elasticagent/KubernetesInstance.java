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

package com.example.elasticagent;

import com.example.elasticagent.requests.CreateAgentRequest;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import java.util.*;

import static com.example.elasticagent.Constants.CREATED_BY_LABEL_KEY;
import static com.example.elasticagent.Constants.ENVIRONMENT_LABEL_KEY;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class KubernetesInstance {
    private final DateTime createdAt;
    private final String environment;
    private String name;

    public KubernetesInstance(String name, Date createdAt, String environment) {
        this.name = name;
        this.createdAt = new DateTime(createdAt);
        this.environment = environment;
    }

    public String name() {
        return name;
    }

    public DateTime createdAt() {
        return createdAt;
    }

    public String environment() {
        return environment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        KubernetesInstance that = (KubernetesInstance) o;

        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    public static KubernetesInstance create(CreateAgentRequest request, PluginSettings settings) {
        String containerName = "kubernetes-elastic-agent" + UUID.randomUUID().toString();
        Date createdAt = new Date();

        HashMap<String, String> labels = labelsFrom(request, createdAt);
        String imageName = image(request.properties());
        List<String> env = environmentFrom(request, settings, containerName);
        List<EnvVar> envVars = new ArrayList<>();

        for (String e : env) {
            String[] split = e.split("=");
            envVars.add(new EnvVar(split[0], split[1], null));
        }


        Config build = new ConfigBuilder().withMasterUrl(settings.getKubernetesClusterUrl()).build();
        KubernetesClient client = new DefaultKubernetesClient(build);

        final Container GoCD_CONTAINER = new Container(null, null, envVars, imageName, "IfNotPresent", null, null, containerName, null, null, null, null, null, null, null, false, null, null);

        ObjectMeta podMetadata = new ObjectMeta();

        podMetadata.setLabels(labels);
        podMetadata.setName(containerName);

        PodSpec podSpec = new PodSpec();
        podSpec.setContainers(new ArrayList<Container>(){{ add(GoCD_CONTAINER); }});
        PodStatus podStatus = new PodStatus();
        Pod elasticAgentPod = new Pod("v1", "Pod", podMetadata, podSpec, podStatus);

        client.pods().inNamespace("default").create(elasticAgentPod);

        return fromInstanceInfo(elasticAgentPod);
    }

    static KubernetesInstance fromInstanceInfo(Pod elasticAgentPod) {
        ObjectMeta metadata = elasticAgentPod.getMetadata();
        String containerName = metadata.getName();
        Date createdAt = new Date();
        createdAt.setTime(Long.valueOf(metadata.getLabels().get("created_at")));
        String environment = metadata.getLabels().get(ENVIRONMENT_LABEL_KEY);
        return new KubernetesInstance(containerName, createdAt, environment);
    }

    private static List<String> environmentFrom(CreateAgentRequest request, PluginSettings settings, String containerName) {
        Set<String> env = new HashSet<>();

//        env.addAll(settings.getEnvironmentVariables());
//        if (StringUtils.isNotBlank(request.properties().get("Environment"))) {
//            env.addAll(splitIntoLinesAndTrimSpaces(request.properties().get("Environment")));
//        }

        env.addAll(Arrays.asList(
                "GO_EA_MODE=" + mode(),
                "GO_EA_SERVER_URL=" + settings.getGoServerUrl()
        ));

        env.addAll(request.autoregisterPropertiesAsEnvironmentVars(containerName));

        return new ArrayList<>(env);
    }

    private static HashMap<String, String> labelsFrom(CreateAgentRequest request, Date createdAt) {
        HashMap<String, String> labels = new HashMap<>();

        labels.put(CREATED_BY_LABEL_KEY, Constants.PLUGIN_ID);
        if (StringUtils.isNotBlank(request.environment())) {
            labels.put(ENVIRONMENT_LABEL_KEY, request.environment());
        }

        labels.put("created_at", String.valueOf(createdAt.getTime()));

        labels.put("kind", "kubernetes-elastic-agent");

        return labels;
    }

    private static String mode() {
        if ("false".equals(System.getProperty("rails.use.compressed.js"))) {
            return "dev";
        }

        if ("true".equalsIgnoreCase(System.getProperty("rails.use.compressed.js"))) {
            return "prod";
        }

        return "";
    }

    private static String image(Map<String, String> properties) {
        String image = properties.get("Image");

        if (isBlank(image)) {
            throw new IllegalArgumentException("Must provide `Image` attribute.");
        }

        if (!image.contains(":")) {
            return image + ":latest";
        }
        return image;
    }
}
