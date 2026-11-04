# Eventide Music HUD: prints what Windows' media controls say is playing, one JSON line a second.
# Works with any app that shows up in the Windows volume / media flyout (Spotify, browsers,
# Apple Music, Media Player and so on). Exits when the game (ParentId) closes.
#
# Each line has the song, its position, Windows' volume (0-100, and whether it's muted), and (once
# per song or app) the cover and the app's icon as base64 PNGs. When a cover, icon or the volume
# can't be read, artError / iconError / volumeError say why.
#
# The game asks for play/pause, next and previous by writing "toggle", "next" or "previous" into
# CommandFile; it's checked five times a second and acted on through the same media controls.
param(
	[int]$ParentId = 0,
	[switch]$Once,
	[string]$CommandFile = ''
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

function Write-Line([string]$line) {
	[Console]::Out.WriteLine($line)
	[Console]::Out.Flush()
}

function Write-Problem([string]$message) {
	Write-Line ('{"error":' + (ConvertTo-Json -Compress $message) + '}')
}

try {
	Add-Type -AssemblyName System.Runtime.WindowsRuntime
	Add-Type -AssemblyName System.Drawing
	$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
	$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
	$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
	$null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
	$null = [Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
	$null = [Windows.Storage.Streams.IInputStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
} catch {
	Write-Problem "Windows media controls aren't available: $($_.Exception.Message)"
	exit 1
}

# Windows' main volume, through the Core Audio API. Without a sound device there's just no volume.
$volumeError = $null
try {
	Add-Type -Language CSharp -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace EventideAudio {
	[Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
	interface IAudioEndpointVolume {
		int RegisterControlChangeNotify();
		int UnregisterControlChangeNotify();
		int GetChannelCount();
		int SetMasterVolumeLevel();
		int SetMasterVolumeLevelScalar(float level, Guid context);
		int GetMasterVolumeLevel();
		int GetMasterVolumeLevelScalar(out float level);
		int SetChannelVolumeLevel();
		int SetChannelVolumeLevelScalar();
		int GetChannelVolumeLevel();
		int GetChannelVolumeLevelScalar();
		int SetMute([MarshalAs(UnmanagedType.Bool)] bool mute, Guid context);
		int GetMute(out bool mute);
	}
	[Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
	interface IMMDevice {
		int Activate(ref Guid id, int context, int parameters, out IAudioEndpointVolume endpoint);
	}
	[Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
	interface IMMDeviceEnumerator {
		int EnumAudioEndpoints();
		int GetDefaultAudioEndpoint(int flow, int role, out IMMDevice device);
	}
	[ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
	class MMDeviceEnumerator {
	}
	public static class Volume {
		static IAudioEndpointVolume Endpoint() {
			IMMDeviceEnumerator devices = (IMMDeviceEnumerator) new MMDeviceEnumerator();
			IMMDevice device;
			Marshal.ThrowExceptionForHR(devices.GetDefaultAudioEndpoint(0, 1, out device));
			IAudioEndpointVolume endpoint;
			Guid id = typeof(IAudioEndpointVolume).GUID;
			Marshal.ThrowExceptionForHR(device.Activate(ref id, 23, 0, out endpoint));
			return endpoint;
		}
		public static float Level() {
			float level;
			Marshal.ThrowExceptionForHR(Endpoint().GetMasterVolumeLevelScalar(out level));
			return level;
		}
		public static bool Muted() {
			bool muted;
			Marshal.ThrowExceptionForHR(Endpoint().GetMute(out muted));
			return muted;
		}
	}
}
'@
	$volumeReader = $true
} catch {
	$volumeReader = $false
	$volumeError = $_.Exception.Message
}

$asTaskMethod = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
	$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
	$_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

# WinRT objects in Windows PowerShell don't always have Dispose(); a failed clean-up mustn't lose a cover
function Close-Quietly($object) {
	if ($null -eq $object) { return }
	try { $object.Dispose() } catch { try { $object.Close() } catch { } }
}

function Wait-Async($operation, [Type]$resultType) {
	$task = $asTaskMethod.MakeGenericMethod($resultType).Invoke($null, @($operation))
	if (-not $task.Wait(4000)) { return $null }
	return $task.Result
}

# Reads a whole WinRT stream into a byte array. Windows PowerShell can't see the members of a
# stream it only knows by interface, so they're reached through .NET (two ways, in case one fails).
function Read-StreamBytes($stream) {
	$firstProblem = $null
	try {
		$netStream = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead([Windows.Storage.Streams.IInputStream]$stream)
		$copy = New-Object System.IO.MemoryStream
		try {
			$netStream.CopyTo($copy)
		} finally {
			Close-Quietly $netStream
		}
		if ($copy.Length -eq 0) { throw 'the image is empty' }
		return , $copy.ToArray()
	} catch {
		$firstProblem = $_.Exception.Message
	}
	try {
		$streamType = [Windows.Storage.Streams.IRandomAccessStream]
		$size = [uint32]$streamType.GetProperty('Size').GetValue($stream)
		if ($size -eq 0) { throw 'the image is empty' }
		$inputStream = $streamType.GetMethod('GetInputStreamAt').Invoke($stream, @([uint64]0))
		$reader = [Windows.Storage.Streams.DataReader]::new($inputStream)
		try {
			$loaded = Wait-Async ($reader.LoadAsync($size)) ([uint32])
			if ($null -eq $loaded -or $loaded -eq 0) { throw 'reading the image timed out' }
			$bytes = New-Object byte[] $loaded
			$reader.ReadBytes($bytes)
			return , $bytes
		} finally {
			Close-Quietly $reader
		}
	} catch {
		throw "couldn't read the image ($firstProblem; $($_.Exception.Message))"
	}
}

# Any image the system can open -> a size x size PNG (cropped to a square from the middle)
function Convert-ToPng([byte[]]$bytes, [int]$size) {
	$memory = New-Object System.IO.MemoryStream (, $bytes)
	$image = $null
	$bitmap = $null
	$graphics = $null
	$output = $null
	try {
		$image = [System.Drawing.Image]::FromStream($memory)
		$side = [Math]::Min($image.Width, $image.Height)
		$source = New-Object System.Drawing.Rectangle ([int](($image.Width - $side) / 2)), ([int](($image.Height - $side) / 2)), $side, $side
		$bitmap = New-Object System.Drawing.Bitmap $size, $size
		$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
		$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
		$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
		$graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
		# Without this, the resize blends in see-through pixels from outside the picture: a faint line along its edges
		$attributes = New-Object System.Drawing.Imaging.ImageAttributes
		$attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
		$graphics.DrawImage($image, (New-Object System.Drawing.Rectangle 0, 0, $size, $size), $source.X, $source.Y, $source.Width, $source.Height, [System.Drawing.GraphicsUnit]::Pixel, $attributes)
		$attributes.Dispose()
		$output = New-Object System.IO.MemoryStream
		$bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
		return [Convert]::ToBase64String($output.ToArray())
	} finally {
		foreach ($item in @($graphics, $bitmap, $image, $output, $memory)) { Close-Quietly $item }
	}
}

function Get-Cover($properties) {
	if ($null -eq $properties.Thumbnail) { throw 'the app has no cover for this yet' }
	$stream = Wait-Async ($properties.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
	if ($null -eq $stream) { throw 'opening the cover timed out' }
	try {
		$bytes = Read-StreamBytes $stream
	} finally {
		Close-Quietly $stream
	}
	return Convert-ToPng $bytes 160
}

# Windows names apps by an app id: "Spotify.exe", "Chrome", "MSEdge", a packaged app's
# "Family!App", or a path that starts with a known folder's id
$knownFolders = @{
	'{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}' = [Environment]::GetFolderPath('System')
	'{6D809377-6AF0-444B-8957-A3773F02200E}' = $env:ProgramW6432
	'{7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E}' = ${env:ProgramFiles(x86)}
	'{F38BF404-1D43-42F2-9305-67DE0B28FC23}' = [Environment]::GetFolderPath('Windows')
	'{905E63B6-C1BF-494E-B29C-65B732D3D21A}' = $env:ProgramFiles
}
$processNames = @{
	'308046B0AF4A39CB' = 'firefox'
	'MSEdge' = 'msedge'
	'Chrome' = 'chrome'
	'Brave' = 'brave'
	'Opera' = 'opera'
	'OperaGX' = 'opera'
	'Vivaldi' = 'vivaldi'
}

function Get-ExePath([string]$app) {
	if ($app -match '^(\{[0-9A-Fa-f-]+\})\\(.+)$') {
		$base = $knownFolders[$Matches[1].ToUpper()]
		if ($base) {
			$path = Join-Path $base $Matches[2]
			if (Test-Path -LiteralPath $path -PathType Leaf) { return $path }
		}
	}
	if ($app -match '^[A-Za-z]:\\' -and (Test-Path -LiteralPath $app -PathType Leaf)) { return $app }
	$name = [System.IO.Path]::GetFileNameWithoutExtension(($app -split '\\')[-1])
	if ($processNames.ContainsKey($app)) { $name = $processNames[$app] }
	$process = Get-Process -Name $name -ErrorAction SilentlyContinue | Where-Object { $_.Path } | Select-Object -First 1
	if ($process) { return $process.Path }
	return $null
}

function Get-PackagedLogo([string]$app) {
	$family = ($app -split '!')[0]
	$package = Get-AppxPackage | Where-Object { $_.PackageFamilyName -eq $family } | Select-Object -First 1
	if (-not $package) { return $null }
	[xml]$manifest = Get-Content -LiteralPath (Join-Path $package.InstallLocation 'AppxManifest.xml')
	$relative = $null
	foreach ($application in @($manifest.Package.Applications.Application)) {
		$visual = $application.VisualElements
		if ($visual -and $visual.Square44x44Logo) { $relative = $visual.Square44x44Logo; break }
		if ($visual -and $visual.Square150x150Logo) { $relative = $visual.Square150x150Logo; break }
	}
	if (-not $relative) { $relative = $manifest.Package.Properties.Logo }
	if (-not $relative) { return $null }
	$full = Join-Path $package.InstallLocation $relative
	if (Test-Path -LiteralPath $full -PathType Leaf) { return $full }
	# Packaged logos come as name.scale-100.png, name.targetsize-48.png and so on
	$folder = Split-Path $full
	$stem = [System.IO.Path]::GetFileNameWithoutExtension($full)
	$extension = [System.IO.Path]::GetExtension($full)
	$files = @(Get-ChildItem -LiteralPath $folder -Filter "$stem*$extension" -ErrorAction SilentlyContinue)
	foreach ($preferred in @('targetsize-48', 'targetsize-32', 'scale-200', 'scale-100')) {
		$match = $files | Where-Object { $_.Name -like "*$preferred*" -and $_.Name -notlike '*contrast*' } | Select-Object -First 1
		if ($match) { return $match.FullName }
	}
	if ($files.Count -gt 0) { return $files[0].FullName }
	return $null
}

function Get-AppIcon([string]$app) {
	if ($app -like '*!*') {
		$logo = Get-PackagedLogo $app
		if ($logo) { return Convert-ToPng ([System.IO.File]::ReadAllBytes($logo)) 48 }
	}
	$exe = Get-ExePath $app
	if (-not $exe) { throw "couldn't find the program for $app" }
	$icon = [System.Drawing.Icon]::ExtractAssociatedIcon($exe)
	$bitmap = $icon.ToBitmap()
	$output = New-Object System.IO.MemoryStream
	try {
		$bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
		return Convert-ToPng $output.ToArray() 32
	} finally {
		Close-Quietly $output
		Close-Quietly $bitmap
		Close-Quietly $icon
	}
}

try {
	$manager = Wait-Async ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
} catch {
	Write-Problem "Couldn't open Windows media controls: $($_.Exception.Message)"
	exit 1
}
if ($null -eq $manager) {
	Write-Problem "Windows media controls didn't answer"
	exit 1
}

$lastKey = $null
$coverSent = $false
$coverPolls = 0
$lastApp = $null
$iconSent = $false
$iconTries = 0

function Invoke-MediaCommand([string]$what) {
	$current = $manager.GetCurrentSession()
	if ($null -eq $current) { return }
	switch ($what.Trim()) {
		'toggle' { $null = Wait-Async ($current.TryTogglePlayPauseAsync()) ([bool]) }
		'next' { $null = Wait-Async ($current.TrySkipNextAsync()) ([bool]) }
		'previous' { $null = Wait-Async ($current.TrySkipPreviousAsync()) ([bool]) }
	}
}

while ($true) {
	if ($ParentId -gt 0 -and $null -eq (Get-Process -Id $ParentId -ErrorAction SilentlyContinue)) { exit 0 }
	try {
		$session = $manager.GetCurrentSession()
		if ($null -eq $session) {
			$lastKey = $null
			Write-Line '{"none":true}'
		} else {
			$app = [string]$session.SourceAppUserModelId
			$properties = Wait-Async ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
			$timeline = $session.GetTimelineProperties()
			$playback = $session.GetPlaybackInfo()
			$title = if ($null -ne $properties) { [string]$properties.Title } else { '' }
			$artist = if ($null -ne $properties) { [string]$properties.Artist } else { '' }
			$album = if ($null -ne $properties) { [string]$properties.AlbumTitle } else { '' }
			$key = "$app|$title|$artist|$album"
			if ($key -ne $lastKey) {
				$lastKey = $key
				$coverSent = $false
				$coverPolls = 0
			}
			if ($app -ne $lastApp) {
				$lastApp = $app
				$iconSent = $false
				$iconTries = 0
			}
			$playing = [string]$playback.PlaybackStatus -eq 'Playing'
			$position = $timeline.Position.TotalSeconds
			if ($playing -and $timeline.LastUpdatedTime.Year -gt 2000) {
				$position += ([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalSeconds
			}
			$line = [ordered]@{
				key = $key
				app = $app
				title = $title
				artist = $artist
				album = $album
				playing = $playing
				position = [Math]::Round([Math]::Max(0, $position), 2)
				duration = [Math]::Round(($timeline.EndTime - $timeline.StartTime).TotalSeconds, 2)
			}
			# Browsers often add the cover a few seconds after the title: try every second at first,
			# then every 5 seconds for a minute
			$coverPolls++
			if (-not $coverSent -and $null -ne $properties -and ($coverPolls -le 5 -or ($coverPolls -le 60 -and $coverPolls % 5 -eq 0))) {
				try {
					$line.art = Get-Cover $properties
					$coverSent = $true
				} catch {
					$line.artError = $_.Exception.Message
				}
			}
			if (-not $iconSent -and $iconTries -lt 3) {
				$iconTries++
				try {
					$line.icon = Get-AppIcon $app
					$iconSent = $true
				} catch {
					$line.iconError = $_.Exception.Message
				}
			}
			if ($volumeReader) {
				try {
					$line.volume = [int][Math]::Round([EventideAudio.Volume]::Level() * 100)
					$line.muted = [EventideAudio.Volume]::Muted()
					$volumeError = $null
				} catch {
					if ($null -eq $volumeError) { $line.volumeError = $_.Exception.Message }
					$volumeError = $_.Exception.Message
				}
			} elseif ($volumeError) {
				$line.volumeError = $volumeError
				$volumeError = ''
			}
			Write-Line ($line | ConvertTo-Json -Compress)
		}
	} catch {
		Write-Problem $_.Exception.Message
	}
	if ($Once) { exit 0 }
	for ($wait = 0; $wait -lt 5; $wait++) {
		if ($CommandFile -ne '' -and (Test-Path -LiteralPath $CommandFile)) {
			try {
				$command = [IO.File]::ReadAllText($CommandFile)
				Remove-Item -LiteralPath $CommandFile -Force
				Invoke-MediaCommand $command
			} catch {
				Write-Problem $_.Exception.Message
			}
			break
		}
		Start-Sleep -Milliseconds 200
	}
}
