param([Parameter(Mandatory=$true)][string]$Command, [int]$Port=25590,
      [string]$Properties="$PSScriptRoot/../neoforge/run-server/server.properties")
$ErrorActionPreference = 'Stop'
$passwordLine = Get-Content -LiteralPath $Properties | Where-Object { $_.StartsWith('rcon.password=') } | Select-Object -First 1
if (!$passwordLine) { throw 'RCON password not configured in disposable server profile' }
$qaTcp = [System.Net.Sockets.TcpClient]::new('127.0.0.1', $Port)
try {
    $qaStream = $qaTcp.GetStream()
    $qaStream.ReadTimeout=10000
    function Read-QABytes([int]$count) {
        $data=[byte[]]::new($count); $offset=0
        while($offset -lt $count) { $read=$qaStream.Read($data,$offset,$count-$offset); if($read -le 0){throw 'RCON disconnected'}; $offset+=$read }
        return ,$data
    }
    function Send-QAPacket([int]$type,[string]$body) {
        $bytes=[Text.Encoding]::UTF8.GetBytes($body)
        $memory=[IO.MemoryStream]::new(); $writer=[IO.BinaryWriter]::new($memory)
        $writer.Write([int]($bytes.Length+10)); $writer.Write([int]42); $writer.Write($type); $writer.Write($bytes); $writer.Write([int16]0)
        $packet=$memory.ToArray(); $writer.Dispose(); $memory.Dispose()
        $qaStream.Write($packet,0,$packet.Length)
        $size=[BitConverter]::ToInt32((Read-QABytes 4),0)
        if($size -lt 10 -or $size -gt 65536){throw 'Invalid RCON response'}
        $response=Read-QABytes $size
        if([BitConverter]::ToInt32($response,0) -eq -1){throw 'RCON authentication failed'}
        return [Text.Encoding]::UTF8.GetString($response,8,$size-10)
    }
    $null=Send-QAPacket 3 $passwordLine.Substring(14)
    Send-QAPacket 2 $Command
} finally { $qaTcp.Dispose() }
