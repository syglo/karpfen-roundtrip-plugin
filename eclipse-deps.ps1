# inspect-eclipse-deps.ps1
param(
    [string]$EclipseHome = "D:\!Bachelor\Bachelor\eclipse-modeling-2026-06-R-win32-x86_64\eclipse"
)

$pluginsDir = Join-Path $EclipseHome "plugins"
if (!(Test-Path $pluginsDir)) {
    Write-Error "Eclipse plugins directory not found at: $pluginsDir"
    exit 1
}

Write-Host "Scanning Eclipse Plugins in: $pluginsDir" -ForegroundColor Cyan

$patterns = @(
    "org.eclipse.emf.ecore_*",
    "org.eclipse.emf.common_*",
    "org.eclipse.emf.transaction_*",
    "org.eclipse.emf.workspace_*",
    "org.eclipse.sirius_*",
    "org.eclipse.sirius.diagram_*",
    "org.eclipse.sirius.common.acceleo.aql_*",
    "org.eclipse.acceleo.query_*",
    "org.eclipse.acceleo.aql_*"
)

foreach ($pattern in $patterns) {
    $matches = Get-ChildItem -Path $pluginsDir -Filter ($pattern + ".jar")
    if ($matches) {
        foreach ($m in $matches) {
            Write-Host "[FOUND] " -ForegroundColor Green -NoNewline
            Write-Host $m.Name
        }
    } else {
        Write-Host "[MISSING] " -ForegroundColor Yellow -NoNewline
        Write-Host "$pattern (Not installed in host Eclipse)"
    }
}