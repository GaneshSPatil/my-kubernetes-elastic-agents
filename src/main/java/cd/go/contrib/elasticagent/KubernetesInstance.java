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

package cd.go.contrib.elasticagent;

import cd.go.contrib.elasticagent.requests.CreateAgentRequest;
import cd.go.contrib.elasticagent.utils.Size;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static cd.go.contrib.elasticagent.Constants.KUBERNETES_POD_CREATION_TIME_FORMAT;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class KubernetesInstance {
    private final DateTime createdAt;
    private final String environment;
    private final Map<String, String> properties;
    private String name;

    private KubernetesInstance(String name, Date createdAt, String environment, Map<String, String> properties) {
        this.name = name;
        this.createdAt = new DateTime(createdAt);
        this.environment = environment;
        this.properties = properties;
    }

    public static KubernetesInstance create(CreateAgentRequest request, PluginSettings settings, KubernetesClient client) {
        String containerName = Constants.KUBERNETES_POD_NAME + UUID.randomUUID().toString();
        Date createdAt = new Date();

        Container container = new Container();
        container.setName(containerName);
        container.setEnv(environmentFrom(request, settings, containerName));
        container.setImage(image(request.properties()));
        container.setImagePullPolicy("IfNotPresent");

        ResourceRequirements resources = new ResourceRequirements();
        resources.setLimits(new HashMap<String, Quantity>() {{
            String maxMemory = request.properties().get("MaxMemory");
            if (StringUtils.isNotBlank(maxMemory)) {
                Size mem = Size.parse(maxMemory);
                put("memory", new Quantity(String.valueOf(mem.toMegabytes()), "Mi"));
            }

            String maxCPU = request.properties().get("MaxCPU");
            if (StringUtils.isNotBlank(maxCPU)) {
                put("cpu", new Quantity(maxCPU));
            }
        }});
        container.setResources(resources);

        ObjectMeta podMetadata = new ObjectMeta();

        podMetadata.setLabels(labelsFrom(request));
        podMetadata.setAnnotations(request.properties());
        podMetadata.setName(containerName);

        PodSpec podSpec = new PodSpec();
        podSpec.setContainers(new ArrayList<Container>() {{
            add(container);
        }});
        PodStatus podStatus = new PodStatus();
        Pod elasticAgentPod = new Pod("v1", "Pod", podMetadata, podSpec, podStatus);

        client.pods().inNamespace(Constants.KUBERNETES_NAMESPACE_KEY).create(elasticAgentPod);
        return fromInstanceInfo(elasticAgentPod);
    }

    static KubernetesInstance fromInstanceInfo(Pod elasticAgentPod) {
        try {
            ObjectMeta metadata = elasticAgentPod.getMetadata();
            String containerName = metadata.getName();
            String environment = metadata.getLabels().get(Constants.ENVIRONMENT_LABEL_KEY);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(KUBERNETES_POD_CREATION_TIME_FORMAT);
            Date date = new Date();
            if(StringUtils.isNotBlank(metadata.getCreationTimestamp())) {
                date = simpleDateFormat.parse(metadata.getCreationTimestamp());

            }
            return new KubernetesInstance(containerName, date, environment, metadata.getAnnotations());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<EnvVar> environmentFrom(CreateAgentRequest request, PluginSettings settings, String containerName) {
        ArrayList<EnvVar> env = new ArrayList<>();
        env.add(new EnvVar("GO_EA_SERVER_URL", settings.getGoServerUrl(), null));
        String environment = request.properties().get("Environment");
        if (StringUtils.isNotBlank(environment)) {
            env.addAll(parseEnvironments(environment));
        }
        env.addAll(request.autoregisterPropertiesAsEnvironmentVars(containerName));

        return new ArrayList<>(env);
    }

    private static Collection<? extends EnvVar> parseEnvironments(String environment) {
        ArrayList<EnvVar> envVars = new ArrayList<>();
        for (String env : environment.split("\n")) {
            String[] parts = env.split("=");
            envVars.add(new EnvVar(parts[0], parts[1], null));
        }

        return envVars;
    }

    private static HashMap<String, String> labelsFrom(CreateAgentRequest request) {
        HashMap<String, String> labels = new HashMap<>();

        labels.put(Constants.CREATED_BY_LABEL_KEY, Constants.PLUGIN_ID);
        if (StringUtils.isNotBlank(request.environment())) {
            labels.put(Constants.ENVIRONMENT_LABEL_KEY, request.environment());
        }

        labels.put(Constants.KUBERNETES_POD_KIND_LABEL_KEY, Constants.KUBERNETES_POD_KIND_LABEL_VALUE);

        return labels;
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

    public void terminate(KubernetesClient client) {
        client.pods().inNamespace(Constants.KUBERNETES_NAMESPACE_KEY).withName(name).delete();
    }

    public Map<String, String> getInstanceProperties() {
        return properties;
    }
}
