# k8s

Local Kubernetes runtime config and AWS deployment notes for Mobi.

```
infra/          Helm values for MySQL, Redis, Kafka, and ingress-nginx
base/           Namespace manifest
kind/           kind cluster config
AWS_DEPLOYMENT_NOTES.md   EKS deploy sequence and remaining manual steps
```

The charts under `infra/` are for the local kind cluster only — `scripts/deploy.sh`
installs them for the `local` target and skips them on any cloud, where these are
managed services provisioned by Terraform.

See [BACKEND.md](../BACKEND.md) for the full local setup walkthrough,
[infra/terraform/](../infra/terraform/README.md) for infrastructure provisioning,
and [docs/PORTABILITY.md](../docs/PORTABILITY.md) for how the deployment stays
portable across clouds.
