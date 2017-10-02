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
import com.thoughtworks.go.plugin.api.logging.Logger;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.elasticagent.Constants.*;

public class KubernetesAgentInstances implements AgentInstances<KubernetesInstance> {

    public static final Logger LOG = Logger.getLoggerFor(KubernetesAgentInstances.class);
    private final ConcurrentHashMap<String, KubernetesInstance> instances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, String>> instanceProperties = new ConcurrentHashMap<>();

    private boolean refreshed;
    public Clock clock = Clock.DEFAULT;

    @Override
    public KubernetesInstance create(CreateAgentRequest request, PluginSettings settings) throws Exception {
        KubernetesInstance instance = KubernetesInstance.create(request, settings, kubernetes(settings));
        register(instance, request.properties());

        return instance;
    }

    private KubernetesClient kubernetes(PluginSettings settings) {
        Config build = new ConfigBuilder().withMasterUrl(settings.getKubernetesClusterUrl()).build();
        return new DefaultKubernetesClient(build);
    };

    @Override
    public void terminate(String agentId, PluginSettings settings) throws Exception {
        KubernetesInstance instance = instances.get(agentId);
        if (instance != null) {
            instance.terminate(settings);
        } else {
            LOG.warn("Requested to terminate an instance that does not exist " + agentId);
        }
        instances.remove(agentId);
        instanceProperties.remove(agentId);
    }

    @Override
    public void terminateUnregisteredInstances(PluginSettings settings, Agents agents) throws Exception {
        KubernetesAgentInstances toTerminate = unregisteredAfterTimeout(settings, agents);
        if (toTerminate.instances.isEmpty()) {
            return;
        }

        LOG.warn("Terminating instances that did not register " + toTerminate.instances.keySet());
        for (KubernetesInstance container : toTerminate.instances.values()) {
            terminate(container.name(), settings);
        }
    }

    @Override
    public Agents instancesCreatedAfterTimeout(PluginSettings settings, Agents agents) {
        ArrayList<Agent> oldAgents = new ArrayList<>();
        for (Agent agent : agents.agents()) {
            KubernetesInstance instance = instances.get(agent.elasticAgentId());
            if (instance == null) {
                continue;
            }

            if (clock.now().isAfter(instance.createdAt().plus(settings.getAutoRegisterPeriod()))) {
                oldAgents.add(agent);
            }
        }
        return new Agents(oldAgents);
    }

    @Override
    public void refreshAll(PluginRequest pluginRequest) throws Exception {
        KubernetesClient client = kubernetes(pluginRequest.getPluginSettings());
        PodList list = client.pods().inNamespace(KUBERNETES_NAMESPACE_KEY).list();

        if (!refreshed) {
            for (Pod pod : list.getItems()) {
                Map<String, String> podLabels = pod.getMetadata().getLabels();
                if(podLabels != null) {
                    if(podLabels.containsKey(KUBERNETES_POD_KIND_LABEL_KEY) && podLabels.get(KUBERNETES_NAMESPACE_KEY).equals(KUBERNETES_POD_KIND_LABEL_VALUE)) {
                        register(KubernetesInstance.fromInstanceInfo(pod), getInstanceProperties(pod.getMetadata().getName()));
                    }
                }
            }

            refreshed = true;
        }
    }

    @Override
    public Map<String, String> getInstanceProperties(String instanceName) {
        return instanceProperties.get(instanceName);
    }

    @Override
    public KubernetesInstance find(String agentId) {
        return instances.get(agentId);
    }

    public boolean hasInstance(String agentId) {
        return instances.containsKey(agentId);
    }

    private void register(KubernetesInstance instance, Map<String, String> properties) {
        instances.put(instance.name(), instance);
        instanceProperties.put(instance.name(), properties);
    }

    private KubernetesAgentInstances unregisteredAfterTimeout(PluginSettings settings, Agents knownAgents) throws Exception {
        Period period = settings.getAutoRegisterPeriod();
        KubernetesAgentInstances unregisteredContainers = new KubernetesAgentInstances();
        KubernetesClient client = kubernetes(settings);

        for (String instanceName : instances.keySet()) {
            if (knownAgents.containsAgentWithId(instanceName)) {
                continue;
            }
            Pod pod = client.pods().inNamespace(KUBERNETES_NAMESPACE_KEY).withName(instanceName).get();

            String createdAt = pod.getMetadata().getLabels().get(POD_CREATED_AT_LABEL_KEY);
            DateTime dateTimeCreated = new DateTime(Long.valueOf(createdAt));

            if (clock.now().isAfter(dateTimeCreated.plus(period))) {
                unregisteredContainers.register(KubernetesInstance.fromInstanceInfo(pod), getInstanceProperties(instanceName));
            }
        }
        return unregisteredContainers;
    }
}