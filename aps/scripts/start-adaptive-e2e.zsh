#!/bin/zsh
# Local disposable infrastructure only. Requires Docker, Java 17 and Maven.
set -euo pipefail
cd "${0:A:h:h:h}"
if [[ "${1:-}" != '--disposable' ]]; then
  print 'Usage: JAVA_HOME=<jdk17> MVN=<mvn> aps/scripts/start-adaptive-e2e.zsh --disposable'
  exit 2
fi
for name in aps-ade-e2e-mysql aps-ade-e2e-redis; do
  if docker inspect "$name" >/dev/null 2>&1; then
    print "Container $name already exists. Inspect/remove the disposable environment explicitly before rerunning."
    exit 2
  fi
done
"${MVN:-mvn}" -B -ntp -f aps/pom.xml clean verify
(cd aps-ui && npm ci && npm test && npm run build)
docker run -d --name aps-ade-e2e-mysql -p 127.0.0.1:3317:3306 \
  -e MYSQL_ROOT_PASSWORD=ade-local-only -e MYSQL_DATABASE=aps_e2e mysql:8.4
docker run -d --name aps-ade-e2e-redis -p 127.0.0.1:6387:6379 \
  redis:7-alpine redis-server --requirepass ade-local-only
ready=0
for attempt in {1..60}; do
  if docker exec -e MYSQL_PWD=ade-local-only aps-ade-e2e-mysql mysql -uroot -e 'SELECT 1' >/dev/null 2>&1; then
    ready=1; break
  fi
  sleep 1
done
[[ "$ready" == 1 ]] || { print 'MySQL did not become ready'; exit 1; }
for schema in aps/sql/jvs-aps.sql aps/sql/20261113_hybrid_solve_audit.sql aps/sql/20261120_demand_planning.sql aps/sql/20261121_launch_review.sql; do
  docker exec -i -e MYSQL_PWD=ade-local-only aps-ade-e2e-mysql mysql -uroot aps_e2e < "$schema"
done
run_dir=$(mktemp -d /tmp/aps-adaptive-run.XXXXXX)
cp aps/jvs-aps-mgr/target/jvs-aps-mgr-1.0.jar "$run_dir/app.jar"
exec "${JAVA_HOME}/bin/java" -Xmx2g -jar "$run_dir/app.jar" \
  --spring.config.additional-location=file:aps/scripts/e2e-local.yml \
  '--spring.datasource.url=jdbc:mysql://127.0.0.1:3317/aps_e2e?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
  --spring.datasource.password=ade-local-only --spring.redis.port=6387 \
  --spring.redis.password=ade-local-only --aps.small-model.enabled=false \
  --logging.config=file:aps/scripts/e2e-logback.xml
