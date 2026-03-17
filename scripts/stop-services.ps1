$ErrorActionPreference = "SilentlyContinue"

# Stops Spring Boot apps started via mvnw spring-boot:run in separate PowerShell windows.
$patterns = @(
    "spring-boot:run",
    "services/order-service",
    "services/inventory-service",
    "services/payment-service",
    "services/notification-service",
    "com.orderflow.orderservice.OrderServiceApplication",
    "com.orderflow.inventoryservice.InventoryServiceApplication",
    "com.orderflow.paymentservice.PaymentServiceApplication",
    "com.orderflow.notificationservice.NotificationServiceApplication"
)

function Test-MatchingProcess {
    param($Process)

    $cmd = $Process.CommandLine
    if (-not $cmd) {
        return $false
    }

    return ($patterns | Where-Object { $cmd -like "*$_*" }).Count -gt 0
}

Get-CimInstance Win32_Process |
    Where-Object {
        if ($_.Name -ine "powershell.exe") {
            return $false
        }

        return (Test-MatchingProcess $_)
    } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

Get-CimInstance Win32_Process |
    Where-Object {
        if ($_.Name -ine "java.exe") {
            return $false
        }

        return (Test-MatchingProcess $_)
    } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

Write-Host "Requested stop for service PowerShell and Java processes."

