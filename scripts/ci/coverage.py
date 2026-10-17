"""Conservative rule inputs, including transitive adapters, for each fixture.

Unknown rules/paths select the full matrix. Keep this inventory with the fixtures;
changes to this file themselves require a full run before a new baseline is trusted.
"""
COROUTINES = {"coroutines.pro", "coroutines-swing.pro"}
# The pinned Compose 1.9 fixtures resolve core coroutines and serialization;
# Compose 1.8 (FloatClock) resolves core coroutines without serialization.
COMPOSE = {"coroutines.pro", "serialization.pro"}
COMPOSE_18 = {"coroutines.pro"}
HTTP = {"retrofit3.pro", "okhttp4.pro", "okio3.pro"}
LOGGING = {"slf4j2.pro", "logback.pro", "logback-classic.pro", "logback-janino.pro"}
NATIVE = {"jna.pro", "jna-platform.pro"}
DBUS = {"dbus.pro", "dbus-native.pro", "slf4j2.pro"}

INTEGRATIONS = {
    "serialization": {"serialization.pro"},
    "serialization-core": {"serialization.pro"},
    "retrofit": HTTP,
    "okhttp": {"okhttp5.pro", "okio3.pro"},
    "slf4j": {"slf4j2.pro"},
    "logback-core": {"logback.pro", "logback-janino.pro"},
    "logback-classic": LOGGING,
    "jna": {"jna.pro"},
    "jna-jpms": {"jna.pro"},
    "jna-platform": NATIVE,
    "jna-platform-jpms": NATIVE,
    "dbus-core": {"dbus.pro", "slf4j2.pro"},
    "dbus-transport": DBUS,
    "sqlite": {"sqlite.pro"},
    "llamatik": {"llamatik.pro"},
    # Include wrappers' platform-specific transitive paths, not just direct APIs.
    "filekit": NATIVE | DBUS,
    "sqldelight": {"sqlite.pro"},
    "retrofit-converter": HTTP | {"serialization.pro"},
}
INTEGRATIONS = {name: rules | (set() if name.startswith("serialization") else COMPOSE)
                for name, rules in INTEGRATIONS.items()}

def pairs(name):
    return [name + "-positive", name + "-negative"]

INTEGRATION_CASES = {name: pairs(name) for name in INTEGRATIONS}
for name in ("filekit", "sqldelight", "retrofit-converter"):
    INTEGRATION_CASES[name] = [name + "-positive"]
for name in ("logback-core", "logback-classic"):
    INTEGRATION_CASES[name] += pairs(name + "-1516")
INTEGRATION_CASES["logback-classic"] += ["logback-classic-1516-no-obfuscation"]

FLOATCLOCK = {
    "datastore": COMPOSE_18 | {"datastore-preferences.pro", "okio3.pro"},
    "datastore-okio": COMPOSE_18 | {"okio3.pro"},
    "oshi": COMPOSE_18 | NATIVE | {"oshi.pro", "slf4j2.pro"},
}
FLOATCLOCK_CASES = {"datastore": pairs("datastore"), "datastore-okio": ["datastore-okio-positive"],
                    "oshi": pairs("oshi")}
KZAGENT = {"coroutines-core": {"coroutines.pro"},
           "coroutines-swing": COROUTINES, "jsoup": {"jsoup.pro"}}
KZAGENT_CASES = {"coroutines-core": pairs("coroutines-core"),
                 "coroutines-swing": pairs("coroutines-swing"),
                 "jsoup": ["jsoup-positive", "jsoup-warnings-negative", "jsoup-reflection-negative"]}
REGRESSIONS = {"jna": {"jna.pro"}, "logback": LOGGING, "dbus": {"dbus.pro", "slf4j2.pro"}}
COIL = COMPOSE | {"coil3.pro", "okhttp4.pro", "okio3.pro"}

KNOWN_RULES = set.union(COIL, *INTEGRATIONS.values(), *FLOATCLOCK.values(), *KZAGENT.values(),
                        *REGRESSIONS.values())
SUITES = ("integrations", "kzagent-dependencies", "floatclock-dependencies",
          "rule-regressions", "coil-desktop", "coil-kmp")
OS = ("ubuntu-latest", "windows-latest", "macos-latest")
