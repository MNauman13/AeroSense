# Short dashboard demo

This walkthrough uses only fictional synthetic data and local notes.

1. Start the local services from the repository root with **docker compose up --build -d**.
2. Wait until **docker compose ps** shows the frontend, backend, analytics, and PostgreSQL services healthy.
3. Open http://localhost:5173 and read the persistent synthetic-data notice.
4. Choose **Load synthetic demo data**, then **Run synthetic analysis**.
5. Choose a synthetic rig or UTC date range. Change the chart measurement and inspect the cycle list.
6. Open a flagged cycle to review its stored score, threshold, feature contributions, and synthetic measurement values.
7. Ask: “What should I compare in the synthetic example?” Review the fictional note citation returned by the assistant.
8. Ask: “What is the validated operating limit for an aircraft?” Confirm that the assistant reports insufficient evidence and provides no operating limit.

Every score, measurement, threshold, note, and answer is synthetic demonstration output. None provides engineering or maintenance advice.
