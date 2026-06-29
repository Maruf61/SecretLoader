import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// SecretLoader IntelliJ injection test.
//
// Under the debugger the process inherits your whole machine environment, so we can't tell injected
// secrets from your normal environment by name alone. We diff against a baseline captured from a plain
// (non-debug) run, which doesn't go through the plugin — so the only NEW variables under the debugger are
// the injected secrets. Values are masked by default; set SHOW_VALUES=1 to print them in full.
//
//   1) In a terminal (no debugger):   java Main.java --baseline       (needs JDK 11+)
//   2) Then run/debug Main from the IDE.
public class Main {

    static final Path BASELINE = Paths.get(".env-baseline.json");
    static final Set<String> EXCLUDE = new HashSet<>(Arrays.asList("SECRETLOADER_ENV", "SHOW_VALUES"));

    public static void main(String[] args) throws IOException {
        if (Arrays.asList(args).contains("--baseline")) {
            List<String> keys = new ArrayList<>(System.getenv().keySet());
            Collections.sort(keys);
            Files.write(BASELINE, toJsonArray(keys).getBytes());
            System.out.println("Baseline captured: " + System.getenv().size() + " env vars -> .env-baseline.json");
            System.out.println("Now run/debug Main from the IDE — the injected secrets will be the new variables.");
            return;
        }

        Set<String> baseline = null;
        try {
            baseline = parseJsonArray(new String(Files.readAllBytes(BASELINE)));
        } catch (IOException e) {
            // no baseline yet
        }

        final Set<String> base = baseline;
        List<String> injected = System.getenv().keySet().stream()
                .filter(k -> !EXCLUDE.contains(k))
                .filter(k -> base == null || !base.contains(k))
                .sorted()
                .collect(Collectors.toList());
        boolean showValues = "1".equals(System.getenv("SHOW_VALUES"));

        String line = repeat("=", 56);
        System.out.println(line);
        System.out.println("  SecretLoader — IntelliJ injection test");
        System.out.println(line);
        System.out.println("SECRETLOADER_ENV : " + System.getenv().getOrDefault("SECRETLOADER_ENV", "(not set — using the resolved default)"));

        if (base == null) {
            System.out.println("\n!! No baseline found — run  java Main.java --baseline  in a terminal first.");
        }

        System.out.println("\nInjected vars (" + injected.size() + ")" + (showValues ? "" : ", values masked") + ":");
        if (injected.isEmpty()) {
            System.out.println("  (none — check projectId / env / CLI login, or strict mode aborted the launch)");
        } else {
            for (String k : injected) {
                System.out.println("  " + k + " = " + (showValues ? System.getenv(k) : mask(System.getenv(k))));
            }
        }
        System.out.println(repeat("-", 56));
        System.out.println("it-works");
        if (!showValues) System.out.println("Tip: set SHOW_VALUES=1 in the run configuration to print full values.");
    }

    static String mask(String v) {
        if (v == null) return "";
        if (v.length() <= 4) return repeat("*", v.length());
        return v.substring(0, 2) + repeat("*", Math.min(v.length() - 2, 10));
    }

    static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    static String toJsonArray(List<String> items) {
        return "[" + items.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",")) + "]";
    }

    static Set<String> parseJsonArray(String json) {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(json);
        while (m.find()) out.add(m.group(1));
        return out;
    }
}
