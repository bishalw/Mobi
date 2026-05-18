# k8s

Local Kubernetes runtime config and AWS deployment notes for Mobi.

```
infra/          Helm values for MySQL, Redis, Kafka, and ingress-nginx
base/           Namespace manifest
kind/           kind cluster config
AWS_DEPLOYMENT_NOTES.md   App-level changes required when deploying to EKS
```

See [BACKEND.md](../BACKEND.md) for the full local setup walkthrough and [infra/terraform/](../infra/terraform/README.md) for AWS infrastructure provisioning.
