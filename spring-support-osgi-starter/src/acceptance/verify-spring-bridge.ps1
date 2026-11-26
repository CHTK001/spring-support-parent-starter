$ErrorActionPreference = 'Stop'
$module = [IO.Path]::GetFullPath("$PSScriptRoot\..\..")
$workspace = [IO.Path]::GetFullPath("$module\..\..")
$utils = "$workspace\utils-support-parent-starter\utils-support-core-parent"
$work = "$workspace\temp\engine-concurrency\spring-acceptance"
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.Directory]::CreateDirectory($work) | Out-Null
& mvn -o -q -f "$module\pom.xml" dependency:build-classpath "-Dmdep.outputFile=$work\dependencies.txt"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$deps = [IO.File]::ReadAllText("$work\dependencies.txt", $utf8).Trim()
$deps = (($deps -split ';' | Where-Object { $_ -notmatch '[\\/]logback-(classic|core)[\\/]' }) -join ';')
$out = "$work\classes"
[IO.Directory]::CreateDirectory($out) | Out-Null
$cp = "$out;$utils\utils-support-common-starter\target\classes;$utils\utils-support-osgi-starter\target\classes;$module\target\classes;$deps"
$sources = @("$PSScriptRoot\api\com\example\spi\GreetingApi.java", "$PSScriptRoot\harness\SpringBridgeLifecycle.java")
$compiler = @('--release','21','-encoding','UTF-8','-parameters','-proc:none','-implicit:none','-cp',('"' + $cp.Replace('\','/') + '"'),'-d',('"' + $out.Replace('\','/') + '"'))
$compiler += $sources | ForEach-Object { '"' + $_.Replace('\','/') + '"' }
[IO.File]::WriteAllLines("$work\javac.args", $compiler, $utf8)
& javac "@$work\javac.args"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Output 'SPRING_BRIDGE_JAVAC_EXIT=0 RELEASE=21 SOURCE=src/acceptance'
foreach ($version in @('v1','v2')) {
    $fixtureOut = "$work\$version-classes"
    $resources = "$work\$version-resources\META-INF\extensions"
    [IO.Directory]::CreateDirectory($fixtureOut) | Out-Null
    [IO.Directory]::CreateDirectory($resources) | Out-Null
    [IO.File]::WriteAllText("$resources\com.example.spi.GreetingApi", "fixture.SpringGreeting`n", $utf8)
    $fixtureArgs = @('--release','21','-encoding','UTF-8','-proc:none','-cp',('"' + $cp.Replace('\','/') + '"'),'-d',('"' + $fixtureOut.Replace('\','/') + '"'),('"' + "$PSScriptRoot\fixtures\$version\fixture\SpringGreeting.java".Replace('\','/') + '"'))
    [IO.File]::WriteAllLines("$work\$version-javac.args", $fixtureArgs, $utf8)
    & javac "@$work\$version-javac.args"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $bundleVersion = if ($version -eq 'v1') { '1.0.0' } else { '2.0.0' }
    $manifest = "Manifest-Version: 1.0`r`nBundle-ManifestVersion: 2`r`nBundle-SymbolicName: spring-lifecycle-fixture`r`nBundle-Version: $bundleVersion`r`nDynamicImport-Package: *`r`n`r`n"
    [IO.File]::WriteAllText("$work\$version.mf", $manifest, $utf8)
    & jar --create --file "$work\$version.jar" --manifest "$work\$version.mf" -C $fixtureOut fixture -C "$work\$version-resources" META-INF
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
[IO.File]::WriteAllLines("$work\java.args", @('-Xmx256m','-XX:ActiveProcessorCount=2','-cp',('"' + $cp.Replace('\','/') + '"'),'harness.SpringBridgeLifecycle',$work.Replace('\','/'),"$work/v1.jar".Replace('\','/'),"$work/v2.jar".Replace('\','/')), $utf8)
& java "@$work\java.args"
exit $LASTEXITCODE
