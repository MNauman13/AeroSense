# Optional Google Cloud Run demo

This deployment path runs the existing three containers on Cloud Run, stores the images in Artifact Registry, and connects the Java API to an **already existing** Cloud SQL for PostgreSQL instance. The deploy script does not create or delete a Cloud SQL instance. Cloud SQL has a recurring charge even when the app is idle; review the instance's pricing and cleanup plan before using it.

## What the deployment does

- Builds the analytics and frontend images and a Cloud Run backend image with the fixed-seed, 3-rig, 1,000-cycle synthetic seed files baked in. Cloud Run has no shared Compose volume.
- Deploys analytics, the Java API, and the dashboard in the same region. Each service scales to zero and is capped at one instance by default.
- Runs Flyway against Cloud SQL when the API starts. The dashboard can then seed the synthetic rows into the database.
- Keeps the answer provider in deterministic mode. The cloud deployment needs no model credentials.

The three Cloud Run services are **public** because this prototype has no authentication and the dashboard calls the API through its same-origin proxy. All endpoints and records are fictional demonstration material. Do not put sensitive or real equipment data in this deployment.

## Prerequisites

1. Install the Google Cloud CLI and authenticate with `gcloud auth login`.
2. Select a Google Cloud project with billing enabled and choose a region. The default is `europe-west2`.
3. Have a Cloud SQL for PostgreSQL instance in that same project and region, with an `aerosense` database (or provide another existing database name) and an existing PostgreSQL user. The script intentionally does not create the instance or its database.
4. Put the database user's password in Secret Manager as a secret with an enabled `latest` version. Pass only the secret name to the deployment script; never put the password on the command line or in this repository.
5. Create a backend runtime service account. Grant it `roles/cloudsql.client` on the project and `roles/secretmanager.secretAccessor` on the database-password secret.
6. Ensure the account running the script can enable services, submit Cloud Build jobs, create Artifact Registry repositories, and deploy Cloud Run services. The Cloud Build execution identity needs `roles/artifactregistry.writer` on the image repository. The deployer also needs permission to use the backend runtime service account.

## Deploy

Run from a clean, committed checkout. Replace the example project, Cloud SQL connection name, user, secret name, and service-account email with resources you prepared:

```powershell
.\scripts\deploy-cloud-run.ps1 `
  -ProjectId "your-project-id" `
  -Region "europe-west2" `
  -CloudSqlInstance "your-project-id:europe-west2:aerosense-db" `
  -DatabaseName "aerosense" `
  -DatabaseUser "aerosense_app" `
  -DatabasePasswordSecret "aerosense-db-password" `
  -BackendServiceAccount "aerosense-cloud-run@your-project-id.iam.gserviceaccount.com"
```

The script verifies that the project, regional Cloud SQL instance, database, database user, enabled password secret version, and service account exist. It then enables the required APIs, creates the Artifact Registry repository if needed, submits a reproducible Cloud Build tagged with the commit, and deploys the three public services. The final output is the dashboard URL. Open it and use **Load synthetic demo data** to seed the database.

The script also requires the worktree to be clean so Cloud Build deploys a committed, reviewable revision. It does not change IAM bindings, create a Cloud SQL instance, or manage the database password value.

## Remove the demo services

Deleting the Cloud Run services stops the app but leaves the Cloud SQL instance, database password secret, build history, and Artifact Registry images in place. Cloud SQL continues to incur charges until separately deleted. To remove only the services and image repository:

```powershell
gcloud run services delete aerosense-frontend --region europe-west2 --project your-project-id --quiet
gcloud run services delete aerosense-backend --region europe-west2 --project your-project-id --quiet
gcloud run services delete aerosense-analytics --region europe-west2 --project your-project-id --quiet
gcloud artifacts repositories delete aerosense --location europe-west2 --project your-project-id --quiet
```

Do not delete shared Cloud SQL instances or secrets unless you have confirmed that nothing else uses them.
