#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [Parameter(Mandatory)]
    [string]$AdminEmail,
    [Parameter(Mandatory)]
    [string]$AdminPassword,
    [Parameter(Mandatory)]
    [string]$UserAEmail,
    [Parameter(Mandatory)]
    [string]$UserAPassword,
    [Parameter(Mandatory)]
    [string]$UserBEmail,
    [Parameter(Mandatory)]
    [string]$UserBPassword
)

$ErrorActionPreference = "Stop"
$script:LastStatusCode = 0
$BaseUrl = $BaseUrl.TrimEnd("/")

function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [string]$Token,
        [object]$Body
    )

    $request = @{
        Uri                  = "$BaseUrl$Path"
        Method               = $Method
        SkipHttpErrorCheck   = $true
        StatusCodeVariable   = "responseStatus"
    }
    if ($Token) {
        $request.Headers = @{ Authorization = "Bearer $Token" }
    }
    if ($PSBoundParameters.ContainsKey("Body")) {
        $request.ContentType = "application/json"
        $request.Body = ConvertTo-Json -InputObject $Body -Depth 10 -Compress
    }

    $response = Invoke-RestMethod @request
    $script:LastStatusCode = [int]$responseStatus
    return $response
}

function Assert-Status {
    param([int]$Expected, [string]$Action)
    if ($script:LastStatusCode -ne $Expected) {
        throw "$Action expected HTTP $Expected, received HTTP $script:LastStatusCode."
    }
}

function Register-IfMissing {
    param([string]$Name, [string]$Email, [string]$Password)
    $null = Invoke-Api -Method POST -Path "/auth/register" -Body @{
        name = $Name
        email = $Email
        password = $Password
    }
    if ($script:LastStatusCode -notin @(200, 409)) {
        Assert-Status 200 "Registering $Email"
    }
}

function Get-AccessToken {
    param([string]$Email, [string]$Password)
    $result = Invoke-Api -Method POST -Path "/auth/login" -Body @{
        email = $Email
        password = $Password
    }
    Assert-Status 200 "Signing in"
    if (-not $result.accessToken) {
        throw "Login response did not contain an access token."
    }
    return [string]$result.accessToken
}

Register-IfMissing -Name "Smoke User A" -Email $UserAEmail -Password $UserAPassword
Register-IfMissing -Name "Smoke User B" -Email $UserBEmail -Password $UserBPassword
$userAToken = Get-AccessToken -Email $UserAEmail -Password $UserAPassword
$userBToken = Get-AccessToken -Email $UserBEmail -Password $UserBPassword
$adminToken = Get-AccessToken -Email $AdminEmail -Password $AdminPassword

$productName = "Smoke Test Product $(Get-Date -Format 'yyyyMMddHHmmss')"
$product = Invoke-Api -Method POST -Path "/admin/products" -Token $adminToken -Body @{
    name = $productName
    description = "Temporary item created by the API smoke script."
    price = 12.50
    category = "Smoke Test"
    color = "Indigo"
    stock = 5
}
Assert-Status 201 "Creating a product"
$productId = [long]$product.id

$order = Invoke-Api -Method POST -Path "/user/orders" -Token $userAToken -Body @{
    items = @(@{ productId = $productId; quantity = 1 })
}
Assert-Status 201 "Placing the first order"
$firstOrderId = [long]$order.id

$ownOrder = Invoke-Api -Method GET -Path "/user/orders/$firstOrderId" -Token $userAToken
Assert-Status 200 "Reading the customer's own order"
if ([long]$ownOrder.id -ne $firstOrderId) {
    throw "Order detail response did not match the requested order."
}

$null = Invoke-Api -Method GET -Path "/user/orders/$firstOrderId" -Token $userBToken
Assert-Status 404 "Checking cross-customer order isolation"

$null = Invoke-Api -Method PATCH -Path "/admin/orders/$firstOrderId/status" -Token $adminToken -Body @{
    status = "SHIPPED"
}
Assert-Status 200 "Shipping the first order"
$null = Invoke-Api -Method PATCH -Path "/admin/orders/$firstOrderId/status" -Token $adminToken -Body @{
    status = "DELIVERED"
}
Assert-Status 200 "Delivering the first order"

$cancelOrder = Invoke-Api -Method POST -Path "/user/orders" -Token $userAToken -Body @{
    items = @(@{ productId = $productId; quantity = 1 })
}
Assert-Status 201 "Placing the cancellation test order"
$cancelOrderId = [long]$cancelOrder.id
$null = Invoke-Api -Method PATCH -Path "/admin/orders/$cancelOrderId/status" -Token $adminToken -Body @{
    status = "CANCELLED"
}
Assert-Status 200 "Cancelling the second order"

$productAfterCancel = Invoke-Api -Method GET -Path "/products/$productId"
Assert-Status 200 "Checking restored product stock"
if ([int]$productAfterCancel.stock -ne 4) {
    throw "Expected cancellation to restore stock to 4, found $($productAfterCancel.stock)."
}

$null = Invoke-Api -Method DELETE -Path "/admin/products/$productId" -Token $adminToken
Assert-Status 204 "Soft-deleting the smoke test product"
$activeProducts = Invoke-Api -Method GET -Path "/admin/products" -Token $adminToken
Assert-Status 200 "Checking the active admin product list"
if (@($activeProducts | Where-Object { [long]$_.id -eq $productId }).Count -ne 0) {
    throw "Soft-deleted product still appears in the active admin list."
}

Write-Host "Smoke flow passed: register/login, product create, order ownership, delivery, cancellation/restock, and soft delete."
