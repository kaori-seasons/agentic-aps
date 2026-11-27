#!/bin/zsh
# 本机一次性隔离环境专用。依赖 Docker 里已在运行的 aps-ade-e2e-mysql 与 aps-ade-e2e-redis，
# 以及事先构建好的 aps/jvs-aps-mgr/target/jvs-aps-mgr-1.0.jar。
# 启动后另开终端执行：python3 -I aps/scripts/e2e_shortage.py --base http://127.0.0.1:31820/mgr/jvs-aps
set -euo pipefail
cd "${0:A:h:h:h}"
if [[ "${1:-}" != '--disposable' ]]; then
  print 'Usage: JAVA_HOME=<jdk17> aps/scripts/start-shortage-e2e.zsh --disposable'
  exit 2
fi
port=${APS_E2E_PORT:-31820}
hook=${APS_E2E_HOOK:-http://127.0.0.1:31999}
[[ -f aps/jvs-aps-mgr/target/jvs-aps-mgr-1.0.jar ]] || { print 'backend jar missing: mvn -f aps/pom.xml -pl jvs-aps-mgr -am package'; exit 2; }
# 欠料预警四张表。前一个迁移是幂等的列加宽，已应用过也没有副作用。
for schema in aps/sql/20261125_cpsat_time_precision.sql aps/sql/20261127_shortage_alert.sql; do
  docker exec -i -e MYSQL_PWD=ade-local-only aps-ade-e2e-mysql mysql -uroot aps_e2e < "$schema"
done
exec "${JAVA_HOME}/bin/java" -Xmx2g -jar aps/jvs-aps-mgr/target/jvs-aps-mgr-1.0.jar \
  --server.port=$port \
  --spring.config.additional-location=file:aps/scripts/e2e-local.yml \
  '--spring.datasource.url=jdbc:mysql://127.0.0.1:3317/aps_e2e?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
  --spring.datasource.password=ade-local-only --spring.redis.port=6387 \
  --spring.redis.password=ade-local-only --aps.small-model.enabled=false --aps.demand.jobs-enabled=true \
  --aps.shortage.outbox-enabled=true --aps.shortage.poll-ms=1000 --aps.shortage.sweep-ms=20000 \
  "--aps.shortage.public-base-url=http://127.0.0.1:$port/mgr/jvs-aps" \
  "--aps.shortage.webhooks[0].name=e2e-hook" "--aps.shortage.webhooks[0].url=$hook/hook" \
  "--aps.shortage.webhooks[0].secret=e2e-shortage-secret" \
  "--aps.shortage.webhooks[1].name=e2e-failing" "--aps.shortage.webhooks[1].url=$hook/fail" \
  "--aps.shortage.webhooks[1].secret=e2e-shortage-secret" \
  '--aps.shortage.webhook.retry-backoff-minutes=0,0,0' \
  --logging.config=file:aps/scripts/e2e-logback.xml
