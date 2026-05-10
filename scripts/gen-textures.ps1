# Generate 16x16 PNG textures for dash tool items (5 tiers + mod icon).
# Re-run after editing palette/shape: powershell -ExecutionPolicy Bypass -File scripts\gen-textures.ps1
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$repoRoot = Split-Path -Parent $PSScriptRoot
$root = Join-Path $repoRoot "src/main/resources/assets/bettermove"
$outDir = Join-Path $root "textures/item"
$null = New-Item -ItemType Directory -Force -Path $outDir

function HexC { param($hex)
    $h = $hex.TrimStart("#")
    $r = [Convert]::ToInt32($h.Substring(0,2),16)
    $g = [Convert]::ToInt32($h.Substring(2,2),16)
    $b = [Convert]::ToInt32($h.Substring(4,2),16)
    return [System.Drawing.Color]::FromArgb($r,$g,$b)
}

function MakeTex { param($path, $handleHex, $headHex, $lightHex, $darkHex)
    $bmp = New-Object System.Drawing.Bitmap 16,16
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::Transparent)
    $handle = HexC $handleHex
    $head   = HexC $headHex
    $light  = HexC $lightHex
    $dark   = HexC $darkHex
    $hp = @( @(13,14),@(13,13),@(12,13),@(12,12),@(11,12),@(11,11),@(10,11),@(10,10),@(9,10),@(9,9),@(8,9),@(8,8),@(7,8),@(7,7),@(6,7),@(6,6) )
    foreach($p in $hp){ $bmp.SetPixel($p[0],$p[1],$handle) }
    $sp = @( @(14,15),@(14,14),@(13,12),@(12,11),@(11,10),@(10,9),@(9,8),@(8,7),@(7,6) )
    foreach($p in $sp){ if($p[0] -lt 16 -and $p[1] -lt 16){ $bmp.SetPixel($p[0],$p[1],$dark) } }
    $hf = @( @(3,5),@(4,5),@(2,4),@(3,4),@(4,4),@(5,4),@(2,3),@(3,3),@(4,3),@(5,3),@(3,2),@(4,2) )
    foreach($p in $hf){ $bmp.SetPixel($p[0],$p[1],$head) }
    $ho = @( @(3,1),@(4,1),@(2,2),@(5,2),@(1,3),@(6,3),@(1,4),@(6,4),@(2,5),@(5,5),@(3,6),@(4,6) )
    foreach($p in $ho){ $bmp.SetPixel($p[0],$p[1],$dark) }
    $bmp.SetPixel(3,2,$light)
    $bmp.SetPixel(2,3,$light)
    $g.Dispose()
    $bmp.Save($path,[System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "wrote $path"
}

# (id, handle, head, light, dark)
$tiers = @(
    @("dash_tool_wood",      "#6E4A23","#A57033","#D5A66B","#3D2811"),
    @("dash_tool_copper",    "#6E4A23","#C46B43","#F0A57C","#5A2C18"),
    @("dash_tool_iron",      "#6E4A23","#D8D8D8","#FFFFFF","#5C5C5C"),
    @("dash_tool_diamond",   "#6E4A23","#5EDBD3","#B8FFFA","#1F6F69"),
    @("dash_tool_netherite", "#6E4A23","#4A4144","#7A6D70","#1B1517")
)
foreach($t in $tiers){ MakeTex (Join-Path $outDir ($t[0]+".png")) $t[1] $t[2] $t[3] $t[4] }
MakeTex (Join-Path $root "icon.png") "#6E4A23" "#5EDBD3" "#B8FFFA" "#1F6F69"
