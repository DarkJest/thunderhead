param([switch]$WaitForClients)
$ErrorActionPreference='Stop'
function Invoke-QA([string]$command) { & "$PSScriptRoot/qa-rcon.ps1" -Command $command }
Invoke-QA 'weather thunder 100000'
if($WaitForClients) {
    $ready=$false
    for($attempt=0;$attempt -lt 80;$attempt++) {
        $online=Invoke-QA 'list'
        if(($online -match 'ThunderQA1') -and ($online -match 'ThunderQA2')){$ready=$true;break}
        Start-Sleep -Milliseconds 500
    }
    if(!$ready){throw 'Both QA clients did not connect'}
}
Invoke-QA 'fill -8 80 -8 8 80 8 minecraft:stone'
Invoke-QA 'fill -8 81 -8 8 90 8 minecraft:air'
Invoke-QA 'kill @e[tag=qa_conduction]'
Invoke-QA 'summon minecraft:villager 0.5 81 5.5 {NoAI:1b,OnGround:1b,Tags:["qa_conduction"]}'
Start-Sleep -Milliseconds 500
$before=Invoke-QA 'data get entity @e[tag=qa_conduction,limit=1] Health'
Invoke-QA 'summon minecraft:lightning_bolt 0.5 81 0.5'
Start-Sleep -Milliseconds 200
$connected=Invoke-QA 'data get entity @e[tag=qa_conduction,limit=1] Health'
Invoke-QA 'data merge entity @e[tag=qa_conduction,limit=1] {Health:20.0f}'
Invoke-QA 'fill 0 80 2 0 80 3 minecraft:air'
Start-Sleep -Milliseconds 1200
Invoke-QA 'summon minecraft:lightning_bolt 0.5 81 0.5'
Start-Sleep -Milliseconds 200
$gap=Invoke-QA 'data get entity @e[tag=qa_conduction,limit=1] Health'
"Before: $before"
"Connected: $connected"
"Gap: $gap"
if($before -notmatch '20\.0f' -or $connected -match '20\.0f' -or $gap -notmatch '20\.0f') {throw 'Conduction integration check failed'}
Invoke-QA 'kill @e[tag=qa_conduction]'
