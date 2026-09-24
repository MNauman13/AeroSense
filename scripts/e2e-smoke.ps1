param(
  [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

$health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/health" -TimeoutSec 20
if ($health.status -ne "UP") {
  throw "AeroSense API is not ready (status: $($health.status))."
}

$seed = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/demo-data/seed" -TimeoutSec 180
if ($seed.cycleCount -lt 1) {
  throw "The seed endpoint did not report any synthetic cycles."
}

$analysisRequest = @{
  modelName = "robust-zscore"
  configuration = @{ threshold = 0.65 }
} | ConvertTo-Json -Compress -Depth 5
$analysis = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/analysis-runs" -ContentType "application/json" -Body $analysisRequest -TimeoutSec 180
if ($analysis.status -ne "SUCCEEDED") {
  throw "Synthetic analysis did not succeed (status: $($analysis.status))."
}

$results = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/analysis-runs/$($analysis.id)/results?page=0&size=1" -TimeoutSec 30
if ($results.items.Count -lt 1) {
  throw "The completed analysis did not return any browseable results."
}

$cycleId = $results.items[0].cycleId
$cycle = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/cycles/$cycleId" -TimeoutSec 30
if ($cycle.measurements.Count -lt 1) {
  throw "The cycle detail endpoint did not return measurements."
}

$question = @{
  question = "What should I compare in the synthetic example?"
} | ConvertTo-Json -Compress
$answer = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/assistant/questions" -ContentType "application/json" -Body $question -TimeoutSec 30
if ($answer.insufficientEvidence -or $answer.citations.Count -lt 1) {
  throw "The assistant did not return cited evidence for the synthetic note question."
}

Write-Output "Synthetic smoke flow succeeded."
Write-Output "Seed: $($seed.rigCount) rigs, $($seed.cycleCount) cycles."
Write-Output "Analysis: $($analysis.id), $($analysis.cycleCount) cycles."
Write-Output "Browse: $($cycle.cycleCode), $($cycle.measurements.Count) measurements."
Write-Output "Assistant citations: $($answer.citations.Count)."
Write-Output "All data and results are synthetic demonstration output."
