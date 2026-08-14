# OpenShift Elasticsearch POC

This proof of concept builds and deploys a small Spring Boot application with a temporary Elasticsearch instance inside OpenShift, tests the application through HTTP, then cleans up the temporary environment.

It intentionally avoids Testcontainers, Docker-in-Docker, Knative, Helm, and Oracle. The goal is to prove the application-level lifecycle locally and from GitLab CI:

```text
OpenShift project
├── Elasticsearch deployment + service
├── Spring Boot deployment + service
│   └── calls http://elasticsearch:9200
└── API test job
    └── calls http://poc-app:8080
```

## Prerequisites

- OpenShift Local / CRC installed and running
- `oc` available on `PATH`
- Logged in to the target OpenShift cluster

For CRC, the usual local flow is:

```bash
crc setup
crc start
eval "$(crc oc-env)"
crc console --credentials
oc login -u kubeadmin -p '<password-from-crc>'
```

Verify access:

```bash
oc whoami
```

## Run

From this directory:

```bash
chmod +x run-poc.sh
./run-poc.sh
```

By default the script uses project `test-poc` and deletes it when the script exits.

To use a different project name:

```bash
PROJECT=test-poc-123 ./run-poc.sh
```

To keep the project around for inspection after the run:

```bash
KEEP_PROJECT=true ./run-poc.sh
```

If your account cannot create projects, use an existing project:

```bash
PROJECT=my-existing-project ./run-poc.sh
```

When the project already exists, the script preserves the project and removes only the POC resources. `KEEP_PROJECT=true` preserves both the project and its POC resources for inspection.

## Run from GitLab CI

The included `.gitlab-ci.yml` runs the same lifecycle with the OpenShift CLI. Add these masked or protected CI/CD variables in GitLab:

- `OPENSHIFT_SERVER`: the OpenShift API URL, such as `https://api.cluster.example:6443`
- `OPENSHIFT_TOKEN`: a token for a service account with permission to build and manage the POC resources
- `OPENSHIFT_PROJECT`: an existing OpenShift project dedicated to the CI test

If the cluster uses a private CA, set `OPENSHIFT_LOGIN_ARGS` to `--insecure-skip-tls-verify=true`, or preferably configure the runner to trust the cluster CA. Override `OPENSHIFT_CLI_IMAGE` when the cluster requires a different compatible `oc` version.

The job is serialized per OpenShift project to prevent concurrent pipelines from overwriting its fixed resource names. It always uploads resource state, events, build logs, application logs, Elasticsearch logs, and API-test logs as GitLab artifacts, and its `after_script` performs a second idempotent cleanup pass in case the main script is interrupted.

## Test flow

The runner uploads the repository to an OpenShift binary Docker build. OpenShift builds the application image, deploys Spring Boot alongside Elasticsearch, and runs a black-box API test from a separate curl container.

The API test:

1. Sends `POST /messages` with `Hello Rahul`.
2. Spring Boot stores the message in Elasticsearch.
3. Sends `GET /messages/search?q=Rahul`.
4. Spring Boot searches Elasticsearch.
5. Asserts that the API returns `Hello Rahul`.

This proves HTTP routing, Spring Boot startup, Spring-to-Elasticsearch connectivity, indexing, search, OpenShift networking, and cleanup. No Docker daemon, Docker-in-Docker, or Testcontainers is required by the caller.

Manual cleanup:

```bash
oc delete project test-poc
```

## Files

- `openshift/elasticsearch.yaml` creates the Elasticsearch deployment and service.
- `src/main/java/com/example/poc/Application.java` starts Spring Boot.
- `src/main/java/com/example/poc/MessageController.java` exposes the message API.
- `src/main/java/com/example/poc/MessageService.java` communicates with Elasticsearch.
- `Dockerfile` builds the Spring Boot application image.
- `openshift/app.yaml` creates the Spring Boot deployment and service.
- `openshift/api-test-job.yaml` creates the black-box API test job.
- `src/test/java/ElasticsearchIT.java` retains the direct JUnit/Elasticsearch test from the previous milestone.
- `run-poc.sh` simulates the future CI lifecycle from a laptop.

## Troubleshooting

If Elasticsearch fails on OpenShift, inspect the pod:

```bash
oc get pods
oc describe pod -l app=poc-elasticsearch
oc logs deployment/poc-elasticsearch
oc logs deployment/poc-app
oc logs job/api-test
oc logs buildconfig/poc-app
```

OpenShift's security model can reject images that assume a fixed user or writable filesystem locations. If that happens, the diagnostics from `run-poc.sh` should show the relevant pod events and container logs.
