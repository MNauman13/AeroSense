param(
  [Parameter(Mandatory = $true)]
  [string]$ProjectId,

  [string]$Region = "europe-west2",

  [string]$Repository = "aerosense",

  [Parameter(Mandatory = $true)]
  [string]$CloudSqlInstance,

  [string]$DatabaseName = "aerosense",

  [Parameter(Mandatory = $true)]
  [string]$DatabaseUser,

  [Parameter(Mandatory = $true)]
  [string]$DatabasePasswordSecret,

  [Parameter(Mandatory = $true)]
  [string]$BackendServiceAccount
)

$ErrorActionPreference = "Stop"

function Invoke-Gcloud {
  param([Parameter(Mandatory = $true)][string[]]$Arguments)

  & gcloud @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Google Cloud command failed: gcloud $($Arguments[0])."
  }
}

$gcloud = Get-Command gcloud -ErrorAction SilentlyContinue
if (-not $gcloud) {
  throw "Install and authenticate the Google Cloud CLI before deploying AeroSense."
}

$activeAccount = & gcloud auth list --filter="status:ACTIVE" --format="value(account)" 2>$null
if ($LASTEXITCODE -ne 0 -or -not $activeAccount) {
  throw "No active Google Cloud account. Run gcloud auth login, then retry."
}

$dirtyFiles = git status --porcelain
if ($LASTEXITCODE -ne 0) {
  throw "Run this script from the AeroSense Git repository."
}
if ($dirtyFiles) {
  throw "Commit or discard working-tree changes before deploying so the Cloud Build source is reviewable."
}

$revisionTag = (git rev-parse --short=12 HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or -not $revisionTag) {
  throw "Could not identify the committed source revision."
}

$instanceParts = $CloudSqlInstance.Split(":")
if ($instanceParts.Length -ne 3 -or $instanceParts[0] -ne $ProjectId) {
  throw "CloudSqlInstance must be a connection name in ProjectId,Region,Instance form."
}
if ($instanceParts[1] -ne $Region) {
  throw "Use the Cloud SQL instance's region for Cloud Run to keep the demo services together."
}
$instanceName = $instanceParts[2]

$project = (& gcloud projects describe $ProjectId --format="value(projectId)" 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $project -ne $ProjectId) {
  throw "The active Google Cloud account cannot access project '$ProjectId'."
}

$actualConnectionName = (
  & gcloud sql instances describe $instanceName --project $ProjectId --format="value(connectionName)" 2>$null
).Trim()
if ($LASTEXITCODE -ne 0 -or $actualConnectionName -ne $CloudSqlInstance) {
  throw "The named Cloud SQL instance was not found in the selected project and region."
}

$database = (
  & gcloud sql databases describe $DatabaseName --instance $instanceName --project $ProjectId --format="value(name)" 2>$null
).Trim()
if ($LASTEXITCODE -ne 0 -or $database -ne $DatabaseName) {
  throw "Create the '$DatabaseName' database in the existing Cloud SQL instance before deploying."
}

$sqlUsers = & gcloud sql users list --instance $instanceName --project $ProjectId --format="value(name)" 2>$null
if ($LASTEXITCODE -ne 0 -or $sqlUsers -notcontains $DatabaseUser) {
  throw "Create the selected database user in the existing Cloud SQL instance before deploying."
}

$secretVersionState = (
  & gcloud secrets versions describe latest --secret $DatabasePasswordSecret --project $ProjectId --format="value(state)" 2>$null
).Trim()
if ($LASTEXITCODE -ne 0 -or $secretVersionState -ne "ENABLED") {
  throw "Add an enabled password version to Secret Manager secret '$DatabasePasswordSecret'."
}

$serviceAccount = (
  & gcloud iam service-accounts describe $BackendServiceAccount --project $ProjectId --format="value(email)" 2>$null
).Trim()
if ($LASTEXITCODE -ne 0 -or $serviceAccount -ne $BackendServiceAccount) {
  throw "Create the backend service account before deploying."
}

Write-Host "Enabling Cloud Run, Cloud Build, Artifact Registry, Cloud SQL, and Secret Manager APIs..."
Invoke-Gcloud @(
  "services", "enable", "run.googleapis.com", "cloudbuild.googleapis.com",
  "artifactregistry.googleapis.com", "sqladmin.googleapis.com", "secretmanager.googleapis.com",
  "--project", $ProjectId
)

& gcloud artifacts repositories describe $Repository --location $Region --project $ProjectId --format="value(name)" 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
  Invoke-Gcloud @(
    "artifacts", "repositories", "create", $Repository,
    "--repository-format", "docker", "--location", $Region,
    "--description", "Synthetic AeroSense Cloud Run images", "--project", $ProjectId
  )
}

$imageRoot = "$Region-docker.pkg.dev/$ProjectId/$Repository"
$backendImage = "$imageRoot/aerosense-backend:$revisionTag"
$analyticsImage = "$imageRoot/aerosense-analytics:$revisionTag"
$frontendImage = "$imageRoot/aerosense-frontend:$revisionTag"
$substitutions = "_REGION=$Region,_REPOSITORY=$Repository,_TAG=$revisionTag"

Write-Host "Building the three container images and generating a fixed-seed synthetic fixture..."
Invoke-Gcloud @(
  "builds", "submit", "--project", $ProjectId, "--region", $Region,
  "--timeout", "1800s", "--config", "cloudbuild.cloudrun.yaml",
  "--substitutions", $substitutions, "."
)

Write-Host "Deploying the analytics and fictional-note service..."
Invoke-Gcloud @(
  "run", "deploy", "aerosense-analytics", "--project", $ProjectId, "--region", $Region,
  "--image", $analyticsImage, "--port", "8000", "--allow-unauthenticated",
  "--min", "0", "--max", "1", "--cpu", "1", "--memory", "512Mi", "--timeout", "30",
  "--set-env-vars", "AEROSENSE_ANSWER_PROVIDER=deterministic"
)
$analyticsUrl = (& gcloud run services describe aerosense-analytics --project $ProjectId --region $Region --format="value(status.url)").Trim()
if ($LASTEXITCODE -ne 0 -or -not $analyticsUrl) {
  throw "The analytics service deployed without returning its URL."
}

Write-Host "Deploying the API and connecting it to the existing Cloud SQL instance..."
$databaseUrl = "jdbc:postgresql:///$DatabaseName`?cloudSqlInstance=$CloudSqlInstance&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
$apiEnvironment = "DATABASE_URL=$databaseUrl,DATABASE_USERNAME=$DatabaseUser,ANALYTICS_URL=$analyticsUrl,RETRIEVAL_URL=$analyticsUrl"
$passwordSecretReference = "DATABASE_PASSWORD=${DatabasePasswordSecret}:latest"
Invoke-Gcloud @(
  "run", "deploy", "aerosense-backend", "--project", $ProjectId, "--region", $Region,
  "--image", $backendImage, "--port", "8080", "--allow-unauthenticated",
  "--min", "0", "--max", "1", "--cpu", "1", "--memory", "1Gi", "--timeout", "300",
  "--add-cloudsql-instances", $CloudSqlInstance,
  "--service-account", $BackendServiceAccount,
  "--set-env-vars", $apiEnvironment,
  "--set-secrets", $passwordSecretReference
)
$backendUrl = (& gcloud run services describe aerosense-backend --project $ProjectId --region $Region --format="value(status.url)").Trim()
if ($LASTEXITCODE -ne 0 -or -not $backendUrl) {
  throw "The backend service deployed without returning its URL."
}

Write-Host "Deploying the public dashboard..."
Invoke-Gcloud @(
  "run", "deploy", "aerosense-frontend", "--project", $ProjectId, "--region", $Region,
  "--image", $frontendImage, "--port", "80", "--allow-unauthenticated",
  "--min", "0", "--max", "1", "--cpu", "1", "--memory", "256Mi", "--timeout", "30",
  "--set-env-vars", "AEROSENSE_API_URL=$backendUrl"
)
$frontendUrl = (& gcloud run services describe aerosense-frontend --project $ProjectId --region $Region --format="value(status.url)").Trim()
if ($LASTEXITCODE -ne 0 -or -not $frontendUrl) {
  throw "The dashboard deployed without returning its URL."
}

Write-Host "AeroSense synthetic demo URL: $frontendUrl"
Write-Host "The dashboard, API, and analytics services allow unauthenticated public access."
Write-Host "Cloud SQL remains a separately billed resource and was not created by this script."
