# Kubernetes Elastic agent plugin for GoCD

# Kubernetes Elastic Agent
Kubernetes Elastic Agent Plugin provides production grade support to run GoCD agents on kubernetes cluster.

> **Note:** Kubernetes Elastic Agent Plugin can work with GoCD server running on any environment. 
__(GoCD server on k8s, on bare-metal, on a VM, or on a container)__

## How to get started:
* Start Kubernetes cluster.
```
minikube start
```
* Install Plugin on GoCD Server.
  * Drop plugin under `server/plugins/external` directory.

* Configure plugin settings
  * Specify `Go Server Url` 
  * Specify `Agent auto-register Timeout (in minutes)`
  * Specify `Kubernetes Cluster URL`
  > Find kubernetes cluster url using `kubectl cluster-info`

* Create Elastic Agent Profile using `kubernetes-elastic-agent` plugin

* Create a job with created `elastic-agent-profile`.
* Trigger builds.


## License

```plain
Copyright 2017 ThoughtWorks, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
