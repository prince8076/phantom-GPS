Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$iconDir = Join-Path $root 'src-tauri\icons'
New-Item -ItemType Directory -Force -Path $iconDir | Out-Null

function New-LogoBitmap([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear([System.Drawing.Color]::Transparent)

    $pad = [single]($size * 0.06)
    $radius = [single]($size * 0.22)
    $rect = New-Object System.Drawing.RectangleF($pad, $pad, ($size - 2 * $pad), ($size - 2 * $pad))

    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $radius * 2
    $path.AddArc($rect.X, $rect.Y, $d, $d, 180, 90)
    $path.AddArc($rect.Right - $d, $rect.Y, $d, $d, 270, 90)
    $path.AddArc($rect.Right - $d, $rect.Bottom - $d, $d, $d, 0, 90)
    $path.AddArc($rect.X, $rect.Bottom - $d, $d, $d, 90, 90)
    $path.CloseFigure()

    $bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect,
        [System.Drawing.Color]::FromArgb(255, 13, 27, 45),
        [System.Drawing.Color]::FromArgb(255, 7, 15, 26),
        45
    )
    $g.FillPath($bgBrush, $path)
    $border = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(90, 61, 220, 151), [single]([Math]::Max(1, $size * 0.012)))
    $g.DrawPath($border, $path)

    $ring = [single]($size * 0.31)
    $cx = $size / 2
    $cy = $size / 2
    $ringPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 61, 220, 151), [single]([Math]::Max(1.4, $size * 0.026)))
    $g.DrawEllipse($ringPen, $cx - $ring, $cy - $ring, 2 * $ring, 2 * $ring)

    $tickPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(170, 61, 220, 151), [single]([Math]::Max(1, $size * 0.016)))
    $inner = $ring * 0.82
    $outer = $ring * 1.16
    $g.DrawLine($tickPen, $cx, $cy - $outer, $cx, $cy - $inner)
    $g.DrawLine($tickPen, $cx, $cy + $inner, $cx, $cy + $outer)
    $g.DrawLine($tickPen, $cx - $outer, $cy, $cx - $inner, $cy)
    $g.DrawLine($tickPen, $cx + $inner, $cy, $cx + $outer, $cy)

    $fontSize = [single]($size * 0.30)
    $font = New-Object System.Drawing.Font('Segoe UI', $fontSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 230, 244, 255))
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $textRect = New-Object System.Drawing.RectangleF(0, [single]($size * 0.03), $size, $size)
    $g.DrawString('R', $font, $brush, $textRect, $format)

    $g.Dispose()
    $bgBrush.Dispose()
    $border.Dispose()
    $ringPen.Dispose()
    $tickPen.Dispose()
    $font.Dispose()
    $brush.Dispose()
    $format.Dispose()
    $path.Dispose()
    return $bmp
}

function Save-Resized([System.Drawing.Bitmap]$source, [int]$size, [string]$path) {
    $target = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($target)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.DrawImage($source, 0, 0, $size, $size)
    $g.Dispose()
    $target.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $target.Dispose()
}

$master = New-LogoBitmap 512
$master.Save((Join-Path $iconDir 'icon.png'), [System.Drawing.Imaging.ImageFormat]::Png)
Save-Resized $master 32 (Join-Path $iconDir '32x32.png')
Save-Resized $master 128 (Join-Path $iconDir '128x128.png')
Save-Resized $master 256 (Join-Path $iconDir '128x128@2x.png')

$iconBmp = New-Object System.Drawing.Bitmap(256, 256, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($iconBmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)
$g.DrawImage($master, 0, 0, 256, 256)
$g.Dispose()

$rect = New-Object System.Drawing.Rectangle(0, 0, 256, 256)
$data = $iconBmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = $data.Stride
$pixels = New-Object byte[] ($stride * 256)
[System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $pixels, 0, $pixels.Length)
$iconBmp.UnlockBits($data)

$dibStream = New-Object System.IO.MemoryStream
$writer = New-Object System.IO.BinaryWriter($dibStream)
$writer.Write([UInt32]40)
$writer.Write([Int32]256)
$writer.Write([Int32]512)
$writer.Write([UInt16]1)
$writer.Write([UInt16]32)
$writer.Write([UInt32]0)
$writer.Write([UInt32](256 * 256 * 4))
$writer.Write([Int32]0)
$writer.Write([Int32]0)
$writer.Write([UInt32]0)
$writer.Write([UInt32]0)
for ($y = 255; $y -ge 0; $y--) {
    for ($x = 0; $x -lt 256; $x++) {
        $i = $y * $stride + $x * 4
        $writer.Write($pixels[$i + 2])
        $writer.Write($pixels[$i + 1])
        $writer.Write($pixels[$i])
        $writer.Write($pixels[$i + 3])
    }
}
$maskSize = [int]([Math]::Ceiling(256 / 32.0) * 4 * 256)
$writer.Write((New-Object byte[] $maskSize))
$writer.Flush()
$dib = $dibStream.ToArray()
$writer.Dispose()
$dibStream.Dispose()

$icoStream = New-Object System.IO.MemoryStream
$icoWriter = New-Object System.IO.BinaryWriter($icoStream)
$icoWriter.Write([UInt16]0)
$icoWriter.Write([UInt16]1)
$icoWriter.Write([UInt16]1)
$icoWriter.Write([byte]0)
$icoWriter.Write([byte]0)
$icoWriter.Write([byte]0)
$icoWriter.Write([byte]0)
$icoWriter.Write([UInt16]1)
$icoWriter.Write([UInt16]32)
$icoWriter.Write([UInt32]$dib.Length)
$icoWriter.Write([UInt32]22)
$icoWriter.Write($dib)
$icoWriter.Flush()
[System.IO.File]::WriteAllBytes((Join-Path $iconDir 'icon.ico'), $icoStream.ToArray())
$icoWriter.Dispose()
$icoStream.Dispose()
$master.Dispose()
$iconBmp.Dispose()

Write-Output "Icons written to $iconDir"
