# Setup used for steps 1-5 (reuse for 6-9)

Stack (all throwaway, removed by name afterwards): `docker run -d --rm --name accept-mra-pg` postgres:16-alpine
(user/pw remote_support, db remote_support_demo, 127.0.0.1:15432) and `accept-mra-s3` chrislusf/seaweedfs:4.48
(`server -dir=/data -s3`, dev-only AWS_* keys, 127.0.0.1:18333). Backend: `mvn -q -f backend/pom.xml -DskipTests package`, then
`SPRING_PROFILES_ACTIVE=demo SERVER_PORT=18080 DB_URL=jdbc:postgresql://127.0.0.1:15432/remote_support_demo OBJECT_STORE_ENDPOINT=http://127.0.0.1:18333 java -jar backend/target/backend-0.1.0-SNAPSHOT.jar`.
Frontend: `BACKEND_URL=http://127.0.0.1:18080 npm run build && BACKEND_URL=... npx next start -p 13100`.
NOTE the demo profile also adds Jordan Ellis's 3 waiting invoices, so "All Agents" reads 6 (3 Jordan + 3 Avery) and the
Agents list also shows Jordan, Marcus, Priya (not just Avery/Blake).

Setup through the API (Manager token from POST /api/auth/login, manager@example.com / ChangeMe123!):
- `POST /api/agents {"name":"Avery Quinn","country":"UNITED_STATES","salaryAmount":1000,"username":"avery.quinn@example.com"}`
  -> response carries `id` and the one-time `password` (Avery: kwvj-447k-9dh5). Blake Rowan the same way (blake.rowan@example.com).
- `POST /api/clients {"name":"Alpha Co"}` (Bravo, Charlie, Delta, Echo too); `POST /api/contracts {"clientId","agentId"}`.
  A=Alpha Co, B=Bravo Co, C=Charlie Co (no SIM), D=Delta Co (added later); Blake has Echo Co.
- Postpaid SIMs: `POST /api/contracts/{id}/sim-cards {"number","carrierId":"c0000000-...-0001","flavor":"POSTPAID","postpaidPlanId":"e0000000-...-0001"}`
  (AT&T Unlimited Starter $65.99 on A; T-Mobile c...0002 / Essentials e...0003 $60 on B).
- As Avery: `curl -F file=@bill.pdf;type=application/pdf -F simCardId=<sim> /api/contracts/{contract}/client-invoice/files`
  for A and B, then `POST /api/me/client-invoice-months/YYYY-MM/send` (no body).
- Send back / resend: Manager UI send back; Avery resends B with `POST /api/contracts/{B}/client-invoice/send`.
- DB: `docker exec accept-mra-pg psql -U remote_support remote_support_demo -c "..."`.
Browser: `playwright-cli -s=mgr` (sign in at /login), assertions on snapshots. Cmd-click: `click <ref> --modifiers Meta`.
