output "eks_cluster_name"      { value = module.eks.cluster_name }
output "rds_endpoint"          { value = module.rds.endpoint }
output "redis_endpoint"        { value = module.elasticache.endpoint }
output "kafka_bootstrap"       { value = module.msk.bootstrap_brokers }
output "ecr_repository_urls"   { value = module.ecr.repository_urls }
