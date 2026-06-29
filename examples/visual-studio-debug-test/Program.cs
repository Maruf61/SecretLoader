using System.Text.Json;

// SecretLoader Visual Studio injection test.
//
// Under the debugger, the process inherits your whole machine environment, so we can't tell injected
// secrets from your normal environment by name alone. We diff against a baseline captured from a plain
// (non-debug) run, which doesn't go through the extension — so the only NEW variables under the debugger
// are the injected secrets. Values are masked by default; set SHOW_VALUES=1 to print them in full.
//
//   1) In a terminal (no debugger):   dotnet run -- --baseline
//   2) Then press F5 and pick a profile.

string baselineFile = Path.Combine(AppContext.BaseDirectory, ".env-baseline.json");
var exclude = new HashSet<string>(StringComparer.OrdinalIgnoreCase) { "SECRETLOADER_ENV", "SHOW_VALUES" };

string[] AllKeys() => Environment.GetEnvironmentVariables().Keys.Cast<string>().ToArray();

if (args.Contains("--baseline"))
{
    File.WriteAllText(baselineFile, JsonSerializer.Serialize(AllKeys().OrderBy(k => k).ToArray()));
    Console.WriteLine($"Baseline captured: {AllKeys().Length} env vars -> .env-baseline.json");
    Console.WriteLine("Now press F5 and pick a profile — the injected secrets will be the new variables.");
    return;
}

HashSet<string>? baseline = null;
try { baseline = new HashSet<string>(JsonSerializer.Deserialize<string[]>(File.ReadAllText(baselineFile))!, StringComparer.OrdinalIgnoreCase); }
catch { /* no baseline yet */ }

var candidates = AllKeys().Where(k => !exclude.Contains(k));
var injected = (baseline != null ? candidates.Where(k => !baseline.Contains(k)) : candidates).OrderBy(k => k).ToList();
bool showValues = Environment.GetEnvironmentVariable("SHOW_VALUES") == "1";

static string Mask(string? v)
{
    v ??= "";
    return v.Length <= 4 ? new string('*', v.Length) : v[..2] + new string('*', Math.Min(v.Length - 2, 10));
}

string line = new string('=', 56);
Console.WriteLine(line);
Console.WriteLine("  SecretLoader — Visual Studio injection test");
Console.WriteLine(line);
Console.WriteLine("SECRETLOADER_ENV : " + (Environment.GetEnvironmentVariable("SECRETLOADER_ENV") ?? "(not set — using the resolved default)"));

if (baseline == null)
    Console.WriteLine("\n!! No baseline found — run  dotnet run -- --baseline  in a terminal first, then F5.");

Console.WriteLine($"\nInjected vars ({injected.Count}){(showValues ? "" : ", values masked")}:");
if (injected.Count == 0)
    Console.WriteLine("  (none — check projectId / env / CLI login, or strict mode aborted the launch)");
else
    foreach (var k in injected)
        Console.WriteLine("  " + k + " = " + (showValues ? Environment.GetEnvironmentVariable(k) : Mask(Environment.GetEnvironmentVariable(k))));

Console.WriteLine(new string('-', 56));
Console.WriteLine("it-works");
if (!showValues) Console.WriteLine("Tip: set SHOW_VALUES=1 in the launch profile to print full values.");
